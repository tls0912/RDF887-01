package com.czkuo.rdf88701.application.service.cover;

import com.czkuo.rdf88701.application.mqtt.util.BaseMqttHandlerUtils;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.ocr.OcrImageService;
import com.czkuo.rdf88701.application.service.ocr.OcrImageService.OcrImagesBundle;
import com.czkuo.rdf88701.common.dto.mqtt.command.S073CommandPayload;
import com.czkuo.rdf88701.common.util.ImageUtils;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CoverOcrVerificationService
 * ----------------------------------------------------------------------------
 * 目的：
 * 把「自判 / S073 發送 / ACK 回寫 / 人工判定 / 最終放行矯正」抽成共用服務，
 * 讓各個 generator（TR3、TRx、WBx…）都可以重用同一套 OCR 驗證邏輯。
 * <p>
 * 重點：
 * 1) 流程一定要等到 S073 結果才往下（即使自判已 PASS）
 * - decideFinal(...) 在 S073 未完成（NOT_SENT / SENT）時，一律回 WAIT_S073
 * - 只有 S073 已完成（PASS / FAIL / ERROR）才會進入「自判 > S073 > 人工」的判定
 * <p>
 * 2) 判定權優先序：自判 > S073 結果 > 人工判
 * - 但仍「必須等 S073 完成」後才做最終判定
 * - 自判 PASS：最終 PASS（即使 S073 FAIL 也不翻案）
 * - 自判 FAIL：以 S073 PASS/FAIL/ERROR 決定要 PASS 或進人工
 * - 人工只會在「自判 FAIL 且 S073 FAIL/ERROR」時介入
 * <p>
 * 3) S073 超時重送
 * - 若 s073Status=SENT 且超過 60 秒仍未完成 → 重送一筆新的 S073
 * - maxRetry=3；超過即標記 ERROR，避免永遠 WAIT_S073
 * - 使用 DB 欄位：
 * s073_sent_time / s073_retry_count / s073_last_retry_time / s073_next_retry_time
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoverOcrVerificationService {

    /* =======================================================================================
     *  站點常數（系統固定：上蓋站=Site#12 / Site#14，可在此集中）
     * ======================================================================================= */

    /**
     * 上蓋站 A / B（TR3 使用，但 service 也可用於「找不到 refSite 時」的 fallback）
     */
    public static final String REF_SITE_A = "Site#12";
    public static final String REF_SITE_B = "Site#14";
    private final OcrTaskRepository ocrTaskRepository;
    // =======================================================================================
    // 依賴：Repository / 外部服務
    // =======================================================================================

    /**
     * OCR 驗證紀錄（ocr_verification）資料存取
     */
    private final OcrVerificationRepository ocrVerificationRepository;
    private final OcrManualLogRepository ocrManualLogRepository;

    /**
     * OCR 文字仍存放在 container_data（ocr_text_1 / ocr_text_2）
     */
    private final ContainerDataRepository containerDataRepository;

    /**
     * lot/part/carrier 取自 container_main（alias_code/lot_no/part_no）
     */
    private final ContainerMainRepository containerMainRepository;

    /**
     * 站點是否有容器（例如：Site#10 是否有空）
     */
    private final LocationTrackingRepository locationTrackingRepository;

    /**
     * OCR 影像服務：提供某容器/站點最近一次拍照的 4 張影像（含 dataUrl + filePath）
     */
    private final OcrImageService ocrImageService;

    /**
     * MQTT Command 發送（S073）
     */
    private final MqttCommandService mqttCommandService;

    /**
     * R029 open task：用來覆蓋 S073 欄位（lotId/trayType/trayDesc）
     */
    private final RobotR029TaskRepository r029TaskRepository;
    private final RobotInR029Repository inR029Repository;
    private final RobotInR029LotRepository inR029LotRepository;

    // =======================================================================================
    // 設定：S073 目標系統 / 冷卻時間
    // =======================================================================================

    /**
     * S073 發送目標系統（例如 ase）
     */
    @Value("${s073.target-system:ase}")
    private String s073TargetSystem;

    /**
     * OCR 都不可用時，用此字樣標記，避免後段流程因空值/FAIL 造成異常
     */
    private static final String OCR_VERIFIED_FALLBACK_PREFIX = "OCR_VERIFIED";

    // =======================================================================================
    // S073 超時重送
    // =======================================================================================

    /**
     * S073 超時門檻（秒）：超過這個時間仍是 SENT → 允許重送
     */
    private static final int S073_TIMEOUT_SECONDS = 60;

    /**
     * S073 最多重送次數：超過後直接標記 ERROR，避免永遠 WAIT_S073
     */
    private static final int S073_MAX_RETRY = 3;

    // =======================================================================================
    // S073 冷卻 / 去重：key -> lastSentEpochMs
    // =======================================================================================

    /**
     * 同一條件的 S073 在冷卻期間內不重送（注意：超時重送會繞過這個限制）
     */
    private final Map<String, Long> s073SendMemo = new ConcurrentHashMap<>();

    /**
     * 防止 memo 無限成長（超過即清空一次）
     */
    private static final int MEMO_MAX_SIZE = 500;

    // =======================================================================================
    // Public API：給 Generator / ACK Handler 使用
    // =======================================================================================

    /**
     * 建立或更新 OCR 驗證紀錄（自動欄位快照），並嘗試處理 S073（含超時重送）。
     * <p>
     * 呼叫時機建議：
     * - generator 判斷「進入 OCR 驗證流程」時呼叫（例如 TR3 有 OCR 就呼叫）
     *
     * @param anchorCmId 祖先/錨點容器（找不到祖先就傳 currentCmId）
     * @param md         generator 算好的比對資訊（refSite/refCmId/lane + match flags）
     * @return 最新的 ocr_verification（含剛更新的自動欄位）
     */
    public OcrVerification upsertAutoSnapshotAndHandleS073(Long anchorCmId, MatchDecision md) {

        // 1) 讀 OCR（本體）
        OcrPair currOcr = getOcrPair(anchorCmId);

        // 2) 自判
        boolean fullMatch = md.partMatch && md.ocr1Match && md.ocr2Match;
        boolean badOcr = isOcrInvalidForPass(currOcr);
        boolean localPass = fullMatch && !badOcr;

        // 3) upsert（只更新自動欄位）
        OcrPair refOcr = (md.refCmId != null) ? getOcrPair(md.refCmId) : new OcrPair("", "");
        OcrVerification ctx = upsertAutoFields(anchorCmId, md, currOcr, refOcr, badOcr, localPass);

        // 4) 送 S073（門檻：料號相同 && refSite 不可空）
        String partNo = getTrayTypeByContainerId(anchorCmId);

        // 防呆：本體料號不可空
        boolean canSendS073 = md.partMatch && notBlank(partNo) && notBlank(md.refSite);

        if (canSendS073) {
            requestS073Required(anchorCmId, partNo, currOcr, md, ctx);
        } else {
            // 不送 S073：保持 NOT_SENT（或維持現狀）
            log.info("[OCR][S073] skip: partMatch={} partNo='{}' refSite='{}' cmId={} lane={}",
                    md.partMatch, safe(partNo), md.refSite, anchorCmId, md.lane);
        }

        return ctx;
    }

    /**
     * 最終判定（自判 > S073 結果 > 人工判），但必須等 S073 完成後才會往下判。
     * <p>
     * 「一定要等 S073 結果」：
     * - NOT_SENT / SENT：一律回 WAIT_S073
     * - PASS / FAIL / ERROR：才會進入最終判定
     *
     * @param currentCmId Tray 本體
     * @param ctx         最新 ocr_verification
     * @return 最終決策（generator 依此決定 DROP / 等待 / 不動作）
     */
    public FinalDecision decideFinal(Long currentCmId, OcrVerification ctx) {

        // ---------------------------------------------------------------------
        // (A) 必須等 S073 完成：NOT_SENT / SENT 一律 WAIT_S073
        // ---------------------------------------------------------------------
        String s073 = safe(ctx.getS073Status());
        boolean s073Done = "PASS".equalsIgnoreCase(s073)
                || "FAIL".equalsIgnoreCase(s073)
                || "ERROR".equalsIgnoreCase(s073);

        if (!s073Done) {
            // 注意：即便自判 PASS，也要等 S073 完成
            return FinalDecision.WAIT_S073;
        }

        // ---------------------------------------------------------------------
        // (B) S073 已完成後，套用優先序：自判 > S073 > 人工
        // ---------------------------------------------------------------------

        // (1) 自判 PASS：直接 PASS（最高優先權）
        if ("Y".equalsIgnoreCase(ctx.getLocalPass())) {
            touchFinalResult(ctx, "PASS");
            return FinalDecision.PASS;
        }

        // 走到這裡：自判 FAIL

        // (2) 自判 FAIL + S073 PASS → PASS + OCR 矯正
        if ("PASS".equalsIgnoreCase(s073)) {
            // normalizeOcrAfterFinalPass(currentCmId, ctx, "S073_PASS");
            touchFinalResult(ctx, "PASS");
            return FinalDecision.PASS;
        }

        // (3) 自判 FAIL + S073 FAIL/ERROR → 交由人工
        String manual = safe(ctx.getManualDecision());

        // 第一次進入人工流程：把 N_A → PENDING
        if ("N_A".equalsIgnoreCase(manual)) {
            ctx.setManualDecision("PENDING");
            ctx.setUpdatedTime(LocalDateTime.now());
            ocrVerificationRepository.update(ctx);
            return FinalDecision.NEED_MANUAL;
        }

        // 等待人工
        if ("PENDING".equalsIgnoreCase(manual)) {
            return FinalDecision.WAIT_MANUAL;
        }

        // 人工允許 → PASS（並矯正 OCR）
        if ("ALLOW".equalsIgnoreCase(manual)) {
            // normalizeOcrAfterFinalPass(currentCmId, ctx, "MANUAL_ALLOW");
            upsertOcrManualLog(ctx, manual);
            touchFinalResult(ctx, "PASS");
            return FinalDecision.PASS;
        }

        // 人工阻擋 → BLOCK
        if ("BLOCK".equalsIgnoreCase(manual)) {
            upsertOcrManualLog(ctx, manual);
            touchFinalResult(ctx, "BLOCK");
            return FinalDecision.BLOCK;
        }

        // 人工重來 → RETRY
        if ("RETRY".equalsIgnoreCase(manual)) {
            //touchFinalResult(ctx, "RETRY");
            ocrTaskRepository.findLatestByContainerId(ctx.getContainerMainId()).ifPresent(otx -> {
                if (!ocrTaskRepository.deleteById(otx.getId())) {
                    //log.debug("[COVER_OCR] Retry OCR but ocrTaskRepository.deleteById error. ContainerMainId:{}",
//                            ctx.getContainerMainId());
                }
            });
            ocrTaskRepository.findLatestByContainerId(ctx.getRefContainerId()).ifPresent(otx -> {
                if (!ocrTaskRepository.deleteById(otx.getId())) {
                    //log.debug("[COVER_OCR] Retry OCR but ocrTaskRepository.deleteById error. RefContainerId:{}",
//                            ctx.getRefContainerId());
                }
            });
            if (containerDataRepository.upsertOcr(ctx.getContainerMainId(), "", "")) {
                //log.debug("[COVER_OCR] Retry OCR but containerDataRepository.upsertOcr error. getContainerMainId:{}",
//                        ctx.getRefContainerId());
            }
            if (containerDataRepository.upsertOcr(ctx.getRefContainerId(), "", "")) {
                //log.debug("[COVER_OCR] Retry OCR but containerDataRepository.upsertOcr error. getRefContainerId:{}",
//                        ctx.getRefContainerId());
            }
            //resetAutoFlow(ctx, "人工重來");
            ocrVerificationRepository.deleteById(ctx.getId());
            upsertOcrManualLog(ctx, manual);
            return FinalDecision.RETRY;
        }


        // 其他未知狀態：保守等待人工（避免誤放行）
        return FinalDecision.WAIT_MANUAL;
    }

    // =======================================================================================
    // Decision / Model（給 generator 丟進來的決策結果 + service 回傳的最終決策）
    // =======================================================================================

    /**
     * MatchDecision（由 Generator 計算後傳入 Service）
     * <p>
     * 服務的定位是「吃結果」而非「算 lane/選 ref」：
     * - 這樣其他 generator 可以用不同策略選 refSite/refCmId（例如 TR8/GPx），但共用同一套狀態機。
     * <p>
     * 欄位說明：
     * - partMatch：料號是否一致
     * - ocrReady ：OCR 是否已就緒
     * - ocr1Match：ocrText1(back) 是否一致（1 對 1）
     * - ocr2Match：ocrText2(front) 是否一致（2 對 2）
     * - refSite  ：參考站點（Site#12 / Site#14 / ...）
     * - refCmId  ：參考上蓋容器 id（若可取得）
     * - lane     ：MAIN/SUB/UNKNOWN（主要用於追查，不影響 service 核心行為）
     */
    public static final class MatchDecision {
        public boolean partMatch;
        public boolean ocrReady;
        public boolean ocr1Match;
        public boolean ocr2Match;
        public String refSite;
        public Long refCmId;
        public String lane;
    }

    /**
     * FinalDecision（Service 輸出給 Generator）
     * - PASS  ：可放行（generator 自己確認目的站空位後 DROP）
     * - BLOCK ：不可放行（人工 BLOCK）
     * - WAIT_S073：等 S073 結果
     * - NEED_MANUAL：已建立 PENDING（第一次轉人工）
     * - WAIT_MANUAL：等待人工 ALLOW/BLOCK
     */
    public enum FinalDecision {
        PASS,
        BLOCK,
        WAIT,
        WAIT_S073,
        NEED_MANUAL,
        WAIT_MANUAL,
        RETRY,
    }

    // =======================================================================================
    // Auto fields upsert（避免洗掉人工欄位）
    // =======================================================================================

    /**
     * 建立或更新 ocr_verification 的「自動流程欄位」：
     * <p>
     * 特別注意：
     * - 自動流程會一直跑，因此 update 必須「只動自動欄位」；
     * manualDecision / finalResult 這些人工/最終欄位不可被自動流程覆蓋。
     *
     * @param cmId      Tray 本體 containerMainId
     * @param md        generator 的比對決策
     * @param currOcr   Tray 本體 OCR（text1/back, text2/front）
     * @param refOcr    上蓋 OCR（text1/back, text2/front）
     * @param badOcr    OCR 是否不合格（空/FAIL）
     * @param localPass 自判結果（fullMatch && !badOcr）
     */
    private @NotNull OcrVerification upsertAutoFields(Long cmId,
                                             MatchDecision md,
                                             OcrPair currOcr,
                                             OcrPair refOcr,
                                             boolean badOcr,
                                             boolean localPass) {

        OcrVerification ctx = ocrVerificationRepository.findByContainerMainId(cmId)
                .orElseGet(() -> {
                    // 首次建立：初始化人工欄位（後續自動流程不覆蓋）
                    OcrVerification v = new OcrVerification();
                    v.setContainerMainId(cmId);
                    v.setState("ACTIVE");
                    v.setCarrierId(getCarrierIdByContainerId(cmId));
                    v.setLotId(getLotIdByContainerId(cmId));
                    v.setTrayType(getTrayTypeByContainerId(cmId));

                    v.setManualDecision("N_A");   // 尚未進入人工判定
                    v.setS073Status("NOT_SENT");  // 初始狀態

                    v.setLocalPass("N");
                    v.setBadOcr("N");
                    v.setPartMatch("N");
                    v.setOcr1Match("N");
                    v.setOcr2Match("N");

                    // retry 欄位初始化（保守）
                    v.setS073SentTime(null);
                    v.setS073RetryCount(0);
                    v.setS073LastRetryTime(null);
                    v.setS073NextRetryTime(null);

                    v.setCreatedTime(LocalDateTime.now());
                    v.setUpdatedTime(LocalDateTime.now());
                    ocrVerificationRepository.save(v);
                    return v;
                });

        // 參考站點/容器（供 UI 與追查）
        ctx.setRefSite(md.refSite);
        ctx.setRefContainerId(md.refCmId);

        // OCR 文字快照（供 UI 顯示與追查）
        ctx.setCurrOcrText1(currOcr.t1);
        ctx.setCurrOcrText2(currOcr.t2);
        ctx.setRefOcrText1(refOcr.t1);
        ctx.setRefOcrText2(refOcr.t2);

        // 比對結果快照
        ctx.setPartMatch(md.partMatch ? "Y" : "N");
        ctx.setOcr1Match(md.ocr1Match ? "Y" : "N");
        ctx.setOcr2Match(md.ocr2Match ? "Y" : "N");
        ctx.setBadOcr(badOcr ? "Y" : "N");
        ctx.setLocalPass(localPass ? "Y" : "N");

        ctx.setUpdatedTime(LocalDateTime.now());

        // ⚠️ 只更新自動欄位，保留 manualDecision / finalResult 等人工欄位
        ocrVerificationRepository.updateAutoFields(ctx);
        return ctx;
    }


    private void resetAutoFlow(OcrVerification v, String reason) {

        v.setS073Status("NOT_SENT");
        v.setS073RetryCount(0);
        v.setS073SentTime(null);
        v.setS073LastRetryTime(null);
        v.setS073NextRetryTime(null);

        // 自動判定結果全部清掉
        v.setLocalPass("N");
        v.setBadOcr("N");
        v.setPartMatch("N");
        v.setOcr1Match("N");
        v.setOcr2Match("N");
        v.setUpdatedTime(LocalDateTime.now());
        v.setFinalResult("");
        v.setManualDecision("N_A");
        // ❗ 不碰這些
        // v.getManualDecision()
        // v.getContainerMainId()
        // v.getCarrierId() / lotId

        ocrVerificationRepository.update(v);
    }

    private void upsertOcrManualLog(OcrVerification ctx, String manualDecision) {
        // 首次建立：初始化人工欄位（後續自動流程不覆蓋）
        if("PASS".equalsIgnoreCase(ctx.getFinalResult()))
            return;
        OcrManualLog v = new OcrManualLog();
        v.setContainerMainId(ctx.getContainerMainId());
        v.setCurrOcrText1(ctx.getCurrOcrText1());
        v.setCurrOcrText2(ctx.getCurrOcrText2());
        v.setRefSite(ctx.getRefSite());
        v.setRefContainerId(ctx.getRefContainerId());
        v.setRefOcrText1(ctx.getRefOcrText1());
        v.setRefOcrText2(ctx.getRefOcrText2());
        v.setManualDecision(manualDecision);
        v.setManualBy(ctx.getManualBy());
        v.setManualTime(LocalDateTime.now());
        ocrManualLogRepository.save(v);
    }

    /**
     * finalResult 屬於「整體決策結果」：
     * - 通常由 decideFinal(...) 做出 PASS/BLOCK 等決策時更新
     * - 這裡允許完整 update（因為 finalResult 本來就是業務結論）
     */
    private void touchFinalResult(OcrVerification ctx, String finalResult) {
        ctx.setFinalResult(finalResult);
        ctx.setUpdatedTime(LocalDateTime.now());
        ocrVerificationRepository.update(ctx);
    }

    // =======================================================================================
    // S073 發送（含冷卻 / 先寫 DB 再送 MQTT / 超時重送）
    // =======================================================================================

    /**
     * 影像四格（避免到處用 index）：
     * - back/front 的命名，對應既有的順序：back1, back3, front1, front3
     */
    private static final class ImageQuad {
        final String backOne;
        final String backThree;
        final String frontOne;
        final String frontThree;

        ImageQuad(String backOne, String backThree, String frontOne, String frontThree) {
            this.backOne = backOne;
            this.backThree = backThree;
            this.frontOne = frontOne;
            this.frontThree = frontThree;
        }
    }

    /**
     * list[0..3] → ImageQuad（不足則為 null）
     */
    private ImageQuad toQuad(List<String> list) {
        if (list == null) list = Collections.emptyList();
        String d0 = list.size() > 0 ? list.get(0) : null;
        String d1 = list.size() > 1 ? list.get(1) : null;
        String d2 = list.size() > 2 ? list.get(2) : null;
        String d3 = list.size() > 3 ? list.get(3) : null;
        return new ImageQuad(d0, d1, d2, d3);
    }

    /**
     * 送 S073（含超時重送）
     * <p>
     * 行為摘要：
     * 1) 若已經有 S073 結果（PASS/FAIL/ERROR）→ 不重送
     * 2) 若 s073Status=NOT_SENT → 正常送出
     * 3) 若 s073Status=SENT：
     * - 未超時 → 等待 ACK（不動作）
     * - 超時且 retryCount < maxRetry → 重送（新 tid）
     * - 超時且 retryCount >= maxRetry → 標記 ERROR（避免永遠 WAIT_S073）
     * 4) 冷卻去重：同一條件在 cooldown 內不重送
     * - 但「超時重送」會繞過冷卻限制
     */
    private void requestS073Required(Long currentCmId,
                                     String partNo,
                                     OcrPair currOcr,
                                     MatchDecision md,
                                     OcrVerification ctx) {

        // ------------------------------------------------------------
        // (0) 若已經有最終結果，S073 沒必要再送
        // ------------------------------------------------------------
        if ("PASS".equalsIgnoreCase(ctx.getFinalResult()) || "BLOCK".equalsIgnoreCase(ctx.getFinalResult())) {
            return;
        }

        // ------------------------------------------------------------
        // (1) 若 S073 已完成（PASS/FAIL/ERROR）→ 不重送
        // ------------------------------------------------------------
        String s073 = safe(ctx.getS073Status());
        if ("PASS".equalsIgnoreCase(s073) || "FAIL".equalsIgnoreCase(s073) || "ERROR".equalsIgnoreCase(s073)) {
            return;
        }

        // ------------------------------------------------------------
        // (2) 若狀態為 SENT：判斷是否超時要重送
        // ------------------------------------------------------------
        if ("SENT".equalsIgnoreCase(s073)) {
            RetryDecision rd = decideRetry(ctx);
            if (rd == RetryDecision.WAIT) {
                // 還沒到重送時間 → 等待 ACK
                return;
            }
            if (rd == RetryDecision.EXHAUSTED) {
                // 超過最大重送 → 直接標記 ERROR，避免永遠 WAIT_S073
                markS073ErrorAsDone(ctx, "TIMEOUT_MAX_RETRY");
                log.warn("[OCR][S073] 超時且重送次數已達上限 → 標記 ERROR（視為完成） cmId={}, retryCount={}",
                        currentCmId, ctx.getS073RetryCount());
                return;
            }
        }

        // ------------------------------------------------------------
        // (3) 決定 refSite：優先用 generator 給的 refSite；沒有就 fallback
        // ------------------------------------------------------------
        RefPick refPick = pickReference(md);

        // 回寫 refSite/refContainerId（即使 generator 沒給，也補上，方便 UI/追查）
        ctx.setRefSite(refPick.refSite);
        ctx.setRefContainerId(refPick.refCmId);

        // ------------------------------------------------------------
        // (4) 開始抓圖、組 payload、先寫 DB 再送 MQTT
        // ------------------------------------------------------------
        try {
            // 影像：curr 用 container；ref 優先用 refCmId，否則用 refSite
            Optional<OcrImagesBundle> currImgsOpt = ocrImageService.getLatestImagesForContainer(currentCmId);

            Optional<OcrImagesBundle> refImgsOpt = (refPick.refCmId != null)
                    ? ocrImageService.getLatestImagesForContainer(refPick.refCmId)
                    : (refPick.refSite != null ? ocrImageService.getLatestImagesForLocation(refPick.refSite) : Optional.empty());

            int currN = currImgsOpt.map(b -> b.getDataUrls().size()).orElse(0);
            int refN = refImgsOpt.map(b -> b.getDataUrls().size()).orElse(0);

            // 影像不足：直接標記 ERROR（視為「S073 已完成」的一種結果）
            // - 這樣 decideFinal 會在 ERROR 後進入人工流程，而不是永遠 WAIT_S073
            if (currImgsOpt.isEmpty() || refImgsOpt.isEmpty() || currN == 0 || refN == 0) {
                log.warn("[OCR][S073] 影像不足 → 標記 ERROR（視為完成） currN={}, refN={}, cmId={}, refSite={}, refCmId={}",
                        currN, refN, currentCmId, refPick.refSite, refPick.refCmId);

                ctx.setS073Status("ERROR");
                ctx.setS073ResultCode("IMG_MISSING");
                ctx.setUpdatedTime(LocalDateTime.now());
                ocrVerificationRepository.updateS073Fields(ctx);
                return;
            }

            // 存 DB 用的檔案路徑（只存 path，不存實際影像）
            ImageQuad currPathQ = toQuad(currImgsOpt.get().getFilePaths());
            ImageQuad refPathQ = toQuad(refImgsOpt.get().getFilePaths());

            ctx.setCurrBackOneLightPath(currPathQ.backOne);
            ctx.setCurrBackThreeLightPath(currPathQ.backThree);
            ctx.setCurrFrontOneLightPath(currPathQ.frontOne);
            ctx.setCurrFrontThreeLightPath(currPathQ.frontThree);

            ctx.setRefBackOneLightPath(refPathQ.backOne);
            ctx.setRefBackThreeLightPath(refPathQ.backThree);
            ctx.setRefFrontOneLightPath(refPathQ.frontOne);
            ctx.setRefFrontThreeLightPath(refPathQ.frontThree);

            // MQTT 用 dataUrl/base64
            ImageQuad currDataQ = toQuad(currImgsOpt.get().getDataUrls());
            ImageQuad refDataQ = toQuad(refImgsOpt.get().getDataUrls());

            // ref OCR（用於 payload 的 ANSWER 與比對）
            OcrPair refOcr = (refPick.refCmId != null) ? getOcrPair(refPick.refCmId) : new OcrPair("", "");

            // front/back 比對（front=ocrText2, back=ocrText1）
            boolean frontMatch = strEq(currOcr.t2, refOcr.t2);
            boolean backMatch = strEq(currOcr.t1, refOcr.t1);

            // R029 覆蓋欄位：lotId/trayType/trayDesc
            Optional<R029Overrides> r029Opt = resolveR029OverridesForCarrier(currentCmId);

            String lotId = pickFirstNotBlank(r029Opt.map(o -> o.lotId).orElse(null), getLotIdByContainerId(currentCmId));
            String trayType = pickFirstNotBlank(r029Opt.map(o -> o.trayType).orElse(null), getTrayTypeByContainerId(currentCmId));
            String trayDesc = safe(r029Opt.map(o -> o.trayDesc).orElse(""));

            // 組 message（cover=ref，tray=curr）
            S073CommandPayload.Message msg = buildS073Message(
                    lotId, trayType, trayDesc,
                    currDataQ, refDataQ,
                    currOcr, refOcr,
                    frontMatch, backMatch
            );

            // --------------------------------------------------------
            // (5) 先寫 DB，再送 MQTT（避免對方回覆太快查不到）
            // --------------------------------------------------------

            // 換新 tid（重送也換，避免舊 tid 回來覆蓋不一致）
            String tid = BaseMqttHandlerUtils.generateUniqueTid();

            LocalDateTime now = LocalDateTime.now();

            // SENT 欄位
            ctx.setS073Tid(tid);
            ctx.setS073Status("SENT");
            ctx.setS073ResultCode(null);

            // retry 欄位更新：
            // - 若原本是 SENT 且超時才進來：視為重送 => retry_count++
            // - 若原本是 NOT_SENT：retry_count 維持 0
            String prevStatus = safe(s073);
            if ("SENT".equalsIgnoreCase(prevStatus)) {
                int prevRetry = (ctx.getS073RetryCount() == null) ? 0 : ctx.getS073RetryCount();
                ctx.setS073RetryCount(prevRetry + 1);
                ctx.setS073LastRetryTime(now);
            } else {
                // 首次送
                ctx.setS073SentTime(now);
                ctx.setS073RetryCount((ctx.getS073RetryCount() == null) ? 0 : ctx.getS073RetryCount());
                ctx.setS073LastRetryTime(null);
            }

            // next_retry_time：now + 60s
            ctx.setS073NextRetryTime(now.plusSeconds(S073_TIMEOUT_SECONDS));

            // last_retry_time：若是首次送，維持 null；若是重送，上面已設 now
            // s073_last_retry_time 已在上面處理

            ctx.setUpdatedTime(now);

            // ⚠️ 只更新 S073 欄位（tid/status/paths/retry.../code）
            ocrVerificationRepository.updateS073Fields(ctx);

            // 送 MQTT
            mqttCommandService.sendS073WithTid(
                    s073TargetSystem, tid,
                    msg.getLotId(), msg.getTrayType(), msg.getTrayDesc(),
                    msg
            );

            log.info("[OCR][S073] sent{} tid={}, cmId={}, refSite={}, refCmId={}, partNo={}, lane={}, retryCount={}",
                    "SENT".equalsIgnoreCase(prevStatus) ? "(RETRY)" : "",
                    tid, currentCmId, refPick.refSite, refPick.refCmId, partNo, safe(md.lane),
                    ctx.getS073RetryCount());

        } catch (Exception e) {
            // 發送異常：標記 ERROR（視為完成）→ 讓流程走人工
            log.warn("[OCR][S073] 發送異常 → 標記 ERROR（視為完成） cmId={}, refSite={}, refCmId={}, err={}",
                    currentCmId, md.refSite, md.refCmId, e.toString());

            ctx.setS073Status("ERROR");
            ctx.setS073ResultCode("EXCEPTION");
            ctx.setUpdatedTime(LocalDateTime.now());
            ocrVerificationRepository.updateS073Fields(ctx);
        }
    }

    /**
     * 超時重送決策：
     * - SENT 且 (now >= next_retry_time) → 允許重送（若 retryCount < maxRetry）
     * - SENT 且尚未到 next_retry_time → WAIT
     * - SENT 且 retryCount >= maxRetry → EXHAUSTED
     * <p>
     * 注意：
     * - next_retry_time 若為 null：用 sent_time + timeout 兜底
     * - sent_time 若也為 null：用 updated_time 兜底（保守）
     */
    private RetryDecision decideRetry(OcrVerification ctx) {
        int retryCount = Math.max(ctx.getS073RetryCount(), 0);
        if (retryCount >= S073_MAX_RETRY) return RetryDecision.EXHAUSTED;

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime nextRetry = ctx.getS073NextRetryTime();
        if (nextRetry != null) {
            return now.isBefore(nextRetry) ? RetryDecision.WAIT : RetryDecision.RETRY;
        }

        // next_retry_time 沒寫入時，fallback 用 sent_time / updated_time 推估
        LocalDateTime base = ctx.getS073SentTime();
        if (base == null) base = ctx.getUpdatedTime();
        if (base == null) base = now.minusSeconds(S073_TIMEOUT_SECONDS + 1);

        LocalDateTime due = base.plusSeconds(S073_TIMEOUT_SECONDS);
        return now.isBefore(due) ? RetryDecision.WAIT : RetryDecision.RETRY;
    }

    private enum RetryDecision {
        WAIT,
        RETRY,
        EXHAUSTED
    }

    /**
     * 標記 S073 ERROR（視為完成）
     * - 目的：不要讓流程永遠 WAIT_S073
     */
    private void markS073ErrorAsDone(OcrVerification ctx, String code) {
        LocalDateTime now = LocalDateTime.now();
        ctx.setS073Status("ERROR");
        ctx.setS073ResultCode(code);
        ctx.setUpdatedTime(now);

        // 發生 ERROR 時，也把 next_retry_time 清掉（避免後續誤重送）
        ctx.setS073NextRetryTime(null);

        ocrVerificationRepository.updateS073Fields(ctx);
    }

    /**
     * 參考站點選擇結果（refSite + refCmId）
     */
    private static final class RefPick {
        final String refSite;
        final Long refCmId;

        RefPick(String refSite, Long refCmId) {
            this.refSite = refSite;
            this.refCmId = refCmId;
        }
    }

    /**
     * 選擇參考站點（cover 來源）。
     * <p>
     * 優先序：
     * 1) generator 提供的 md.refSite / md.refCmId（若存在）
     * 2) fallback：
     * - Site#12 有容器 → 用 Site#12
     * - 否則 Site#14 有容器 → 用 Site#14
     * - 都沒有 → refSite=null（後續會導致 IMG_MISSING → ERROR）
     */
    private RefPick pickReference(MatchDecision md) {

        // 1) 先用 generator 指定的 ref
        if (notBlank(md.refSite)) {
            return new RefPick(md.refSite, md.refCmId);
        }

        // 2) fallback：Site#12 優先
        Optional<Long> cm12 = locationTrackingRepository.findContainerAtLocationName(REF_SITE_A);
        if (cm12.isPresent()) return new RefPick(REF_SITE_A, cm12.get());

        Optional<Long> cm14 = locationTrackingRepository.findContainerAtLocationName(REF_SITE_B);
        if (cm14.isPresent()) return new RefPick(REF_SITE_B, cm14.get());

        // 3) 都沒有
        return new RefPick(null, null);
    }

    /**
     * 組 S073 的 Message：
     * - cover=ref（上蓋站 Site#12/14）
     * - tray=curr（目前容器）
     * - ANSWER_*：cover 用 refOcr；tray 用 currOcr
     * - front=ocrText2；back=ocrText1
     * - VENDER_RESULT / VENDER_RESULT_FAIL：依 frontMatch/backMatch 給 PASS/FAIL
     */
    private S073CommandPayload.Message buildS073Message(String lotId,
                                                        String trayType,
                                                        String trayDesc,
                                                        ImageQuad currQ,
                                                        ImageQuad refQ,
                                                        OcrPair currOcr,
                                                        OcrPair refOcr,
                                                        boolean frontMatch,
                                                        boolean backMatch) {

        S073CommandPayload.Message m = new S073CommandPayload.Message();
        m.setLotId(nullToEmpty(lotId));
        m.setTrayType(nullToEmpty(trayType));
        m.setTrayDesc(nullToEmpty(trayDesc));

        // 上蓋影像（UPPER_COVER_*）
        m.setUpperCoverTrayFrontOneLight(toBytes(refQ.frontOne));
        m.setUpperCoverTrayFrontThreeLight(toBytes(refQ.frontThree));
        m.setUpperCoverTrayBackOneLight(toBytes(refQ.backOne));
        m.setUpperCoverTrayBackThreeLight(toBytes(refQ.backThree));

        // Tray 本體影像（TRAY_*）
        m.setTrayFrontOneLight(toBytes(currQ.frontOne));
        m.setTrayFrontThreeLight(toBytes(currQ.frontThree));
        m.setTrayBackOneLight(toBytes(currQ.backOne));
        m.setTrayBackThreeLight(toBytes(currQ.backThree));

        // ANSWER（cover=ref, tray=curr；front=ocrText2, back=ocrText1）
        m.setAnswerUpperCoverTrayFrontOneLight(nullToEmpty(refOcr.t2));
        m.setAnswerUpperCoverTrayFrontThreeLight(nullToEmpty(refOcr.t2));
        m.setAnswerUpperCoverTrayBackOneLight(nullToEmpty(refOcr.t1));
        m.setAnswerUpperCoverTrayBackThreeLight(nullToEmpty(refOcr.t1));

        m.setAnswerTrayFrontOneLight(nullToEmpty(currOcr.t2));
        m.setAnswerTrayFrontThreeLight(nullToEmpty(currOcr.t2));
        m.setAnswerTrayBackOneLight(nullToEmpty(currOcr.t1));
        m.setAnswerTrayBackThreeLight(nullToEmpty(currOcr.t1));

        // VENDER_RESULT / FAIL CODE（F=front mismatch, B=back mismatch）
        if (frontMatch && backMatch) {
            m.setVenderResult("PASS");
            m.setVenderResultFail("");
        } else {
            m.setVenderResult("FAIL");
            StringBuilder fail = new StringBuilder();
            if (!frontMatch) fail.append("F");
            if (!backMatch) fail.append(!fail.isEmpty() ? ",B" : "B");
            m.setVenderResultFail(fail.toString());
        }

        return m;
    }

    /**
     * dataUrl/base64 → byte[]
     * - ImageUtils.base64ToBytes(...) 支援含 data: 前綴
     */
    private byte[] toBytes(String dataUrlOrBase64) {
        if (dataUrlOrBase64 == null || dataUrlOrBase64.isBlank()) return null;
        return ImageUtils.base64ToBytes(dataUrlOrBase64);
    }

    // =======================================================================================
    // OCR 矯正（要求：1對1 / 2對2 不混用）
    // =======================================================================================

    /**
     * 自判 FAIL 但最終 PASS 時，修正 OCR（避免後續流程異常）。
     * <p>
     * 重要規則：
     * - ocrText1(Back) 只能對 ocrText1(Back)
     * - ocrText2(Front) 只能對 ocrText2(Front)
     * - 不允許混用（不能拿 refFront 補 currBack 或反過來）
     * <p>
     * 策略：
     * - backVal：優先 refBack；refBack 不可用才用 currBack；都不可用才用 fallback
     * - frontVal：優先 refFront；refFront 不可用才用 currFront；都不可用才用 fallback
     * - 寫回 current container_data；若 ref 存在也同步（確保同欄位一致）
     */
    private void normalizeOcrAfterFinalPass(Long currentCmId, OcrVerification ctx, String passReason) {

        Long refCmId = ctx.getRefContainerId();

        // current OCR
        ContainerData currCd = containerDataRepository.findByContainerMainId(currentCmId).orElse(null);
        String currBack = (currCd != null) ? safeTrim(currCd.getOcrText1()) : "";
        String currFront = (currCd != null) ? safeTrim(currCd.getOcrText2()) : "";

        // ref OCR
        ContainerData refCd = null;
        String refBack = "";
        String refFront = "";
        if (refCmId != null) {
            refCd = containerDataRepository.findByContainerMainId(refCmId).orElse(null);
            if (refCd != null) {
                refBack = safeTrim(refCd.getOcrText1());
                refFront = safeTrim(refCd.getOcrText2());
            }
        }

        // 選 backVal / frontVal（同欄位，不混用）
        String backVal;
        if (!isBadOcrText(refBack)) backVal = refBack;
        else if (!isBadOcrText(currBack)) backVal = currBack;
        else backVal = buildFallback("BACK", passReason, ctx, currentCmId);

        String frontVal;
        if (!isBadOcrText(refFront)) frontVal = refFront;
        else if (!isBadOcrText(currFront)) frontVal = currFront;
        else frontVal = buildFallback("FRONT", passReason, ctx, currentCmId);

        // 寫回 current
        if (currCd != null) {
            boolean needUpdateCurr = !strEq(currBack, backVal) || !strEq(currFront, frontVal);
            if (needUpdateCurr) {
                currCd.setOcrText1(backVal);
                currCd.setOcrText2(frontVal);
                currCd.setUpdatedTime(LocalDateTime.now());
                containerDataRepository.update(currCd);

                log.info("[OCR][NORMALIZE] current cm#{} reason={} back [{}]→[{}], front [{}]→[{}]",
                        currentCmId, passReason, currBack, backVal, currFront, frontVal);
            }
        } else {
            log.warn("[OCR][NORMALIZE] current cm#{} 找不到 container_data，無法寫回 OCR，reason={}",
                    currentCmId, passReason);
        }

        // 同步 ref（若存在）
        if (refCd != null) {
            String oldRefBack = safeTrim(refCd.getOcrText1());
            String oldRefFront = safeTrim(refCd.getOcrText2());
            boolean needUpdateRef = !strEq(oldRefBack, backVal) || !strEq(oldRefFront, frontVal);

            if (needUpdateRef) {
                refCd.setOcrText1(backVal);
                refCd.setOcrText2(frontVal);
                refCd.setUpdatedTime(LocalDateTime.now());
                containerDataRepository.update(refCd);

                log.info("[OCR][NORMALIZE] ref cm#{} reason={} back [{}]→[{}], front [{}]→[{}]",
                        refCmId, passReason, oldRefBack, backVal, oldRefFront, frontVal);
            }
        }
    }

    /**
     * 兩邊 OCR 都不可用時，用可追查的 fallback 字串填入（避免空值/FAIL）
     */
    private String buildFallback(String side, String passReason, OcrVerification ctx, Long currentCmId) {
        String carrier = safeTrim(ctx.getCarrierId());
        String suffix = carrier.isEmpty() ? String.valueOf(currentCmId) : carrier;
        return OCR_VERIFIED_FALLBACK_PREFIX + "_" + side + "_" + passReason + "_" + suffix;
    }

    /* =======================================================================================
     *  R029 覆蓋欄位（lotId/trayType/trayDesc）
     * ======================================================================================= */

    /**
     * R029 覆蓋欄位（S073 欄位填值用）
     */
    private static final class R029Overrides {
        final String lotId;
        final String trayType;
        final String trayDesc;

        R029Overrides(String lotId, String trayType, String trayDesc) {
            this.lotId = lotId;
            this.trayType = trayType;
            this.trayDesc = trayDesc;
        }
    }

    /**
     * 依「carrierId」回溯 open R029，取 lotId/trayType/trayDesc 供 S073 覆蓋使用。
     * <p>
     * 規則：
     * - 從 open 任務中找「lot 清單包含 carrierId」的任務
     * - PROCESSING 優先，其次第一筆
     * - trayType/trayDesc 取 robot_in_r029（by logId）
     * - lotId 取 robot_in_r029_lot 中與 carrierId 相同的那筆
     */
    private Optional<R029Overrides> resolveR029OverridesForCarrier(Long currentCmId) {
        String carrierId = getCarrierIdByContainerId(currentCmId);
        if (carrierId.isBlank()) return Optional.empty();

        String key = norm(carrierId);

        List<RobotR029Task> open = safeList(r029TaskRepository.findOpen());
        if (open.isEmpty()) return Optional.empty();

        List<RobotR029Task> matched = new ArrayList<>();
        for (RobotR029Task t : open) {
            Long logId = t.getLogId();
            if (logId == null) continue;

            List<String> lots = safeList(inR029LotRepository.findCarrierIdsByLogId(logId));
            boolean hit = lots.stream().filter(Objects::nonNull).map(this::norm).anyMatch(key::equals);
            if (hit) matched.add(t);
        }
        if (matched.isEmpty()) return Optional.empty();

        RobotR029Task ctx = matched.stream()
                .filter(t -> "PROCESSING".equalsIgnoreCase(safe(t.getInternalState())))
                .findFirst()
                .orElse(matched.get(0));

        Long logId = ctx.getLogId();
        if (logId == null) return Optional.empty();

        Optional<RobotInR029> inOpt = inR029Repository.findById(logId);
        String trayType = inOpt.map(RobotInR029::getTrayType).orElse(null);
        String trayDesc = inOpt.map(RobotInR029::getTrayDesc).orElse(null);

        String lotId = safeList(inR029LotRepository.findCarrierIdsByLogId(logId)).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> s.equalsIgnoreCase(carrierId))
                .findFirst()
                .orElse(null);

        return Optional.of(new R029Overrides(lotId, trayType, trayDesc));
    }

    // =======================================================================================
    // OCR / DB helpers（文字、取值、比對）
    // =======================================================================================

    /**
     * OCR 字串對（t1=back, t2=front）
     */
    private static final class OcrPair {
        final String t1;
        final String t2;

        OcrPair(String t1, String t2) {
            this.t1 = t1;
            this.t2 = t2;
        }
    }

    /**
     * 讀 container_data OCR（null→""；trim）
     */
    private OcrPair getOcrPair(Long containerMainId) {
        return containerDataRepository.findByContainerMainId(containerMainId)
                .map(cd -> new OcrPair(safeTrim(cd.getOcrText1()), safeTrim(cd.getOcrText2())))
                .orElse(new OcrPair("", ""));
    }

    /**
     * 放行用 OCR 檢查：
     * - 任一欄空/NULL → 不合格
     * - 任一欄包含 "fail"（不分大小寫）→ 不合格
     */
    public boolean isOcrInvalidForPass(OcrPair p) {
        return isBadOcrText(p.t1) || isBadOcrText(p.t2);
    }

    /**
     * 一般用途：空/含 fail 視為不可用
     */
    private boolean isBadOcrText(String s) {
        String t = safeTrim(s);
        if (t.isEmpty()) return true;
        return t.toLowerCase(Locale.ROOT).contains("fail");
    }

    /**
     * carrierId 來自 container_main.alias_code
     */
    private String getCarrierIdByContainerId(Long containerMainId) {
        return containerMainRepository.findById(containerMainId)
                .map(ContainerMain::getAliasCode)
                .map(s -> s == null ? "" : s.trim())
                .orElse("");
    }

    /**
     * lotId 來自 container_main.lot_no（可空）
     */
    private String getLotIdByContainerId(Long containerMainId) {
        return containerMainRepository.findById(containerMainId)
                .map(ContainerMain::getLotNo)
                .orElse("");
    }

    /**
     * 料號來自 container_main.part_no（必要）
     */
    private String getTrayTypeByContainerId(Long containerMainId) {
        return containerMainRepository.findById(containerMainId)
                .map(ContainerMain::getPartNo)
                .filter(this::notBlank)
                .orElse(null);
    }

    /**
     * null/空白 安全大小寫不敏感比較
     */
    private boolean strEq(String a, String b) {
        return safeTrim(a).equalsIgnoreCase(safeTrim(b));
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String nullToEmpty(String s) {
        return (s == null) ? "" : s;
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    private String norm(String s) {
        return safe(s).trim().toUpperCase(Locale.ROOT);
    }

    private static <T> List<T> safeList(List<T> list) {
        return (list == null) ? Collections.emptyList() : list;
    }

    /**
     * S073 欄位覆蓋用：優先取非空
     */
    private String pickFirstNotBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a;
        return b;
    }
}
