package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.cover.CoverOcrVerificationService;
import com.czkuo.rdf88701.application.service.cover.CoverOcrVerificationService.FinalDecision;
import com.czkuo.rdf88701.application.service.cover.CoverOcrVerificationService.MatchDecision;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.OcrVerification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * CoverOcrVerificationMonitor
 * ----------------------------------------------------------------------------
 * 監控兩組固定配對站點是否需要建立/推進 OCR 驗證：
 * <p>
 * - Pair#1: Site#12 (cover/ref) <-> Site#36 (tray/current)
 * - Pair#2: Site#14 (cover/ref) <-> Site#25 (tray/current)
 * <p>
 * 重要規則（本版新增/修正）：
 * 0) 「上下文 Key」改為 Anchor(祖先) containerId：
 * - 拆批會產生分身（_k / clone / rename），若用自己的 cmId 當 key 會造成重複上下文。
 * - Anchor 取得策略：優先 LINEAGE_ROOT_CMID；沒有就沿 LINEAGE_PARENT_CMID 往上找；都沒有就用自己。
 * <p>
 * 1) 查 existing OcrVerification 不能只靠 anchorCmId：
 * - refContainerId 不保證永遠同一顆（站上容器可能替換/周轉），
 * 所以 existing 必須用「anchorCmId + refCmId」一起當條件，避免誤把舊 ref 的 context 拿來續跑。
 * <p>
 * 2) 行為：
 * A) tray/current 與 cover/ref 兩站都有容器才進入。
 * B) tray/current 必須已具備 OCR（任一欄有值）才進 S073 流程（避免無 OCR 白送/重送）。
 * C) 料號必須一致 partMatch=true 才推進（固定配對站：1對1、2對2、不混用）。
 * D) 將判定交給 CoverOcrVerificationService：
 * - upsert 自動欄位（OCR快照、比對結果、ref資訊）
 * - 視狀態推進 S073（含 timeout/冷卻/重送）
 * <p>
 * 3) 本 Monitor 只做「建/推進 OCR 驗證」，不做搬運/落帳（no DROP）。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoverOcrVerificationMonitor {

    private static final String ATTR_PARENT = "LINEAGE_PARENT_CMID";
    private static final String ATTR_ROOT = "LINEAGE_ROOT_CMID";

    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerMainRepository containerMainRepository;
    private final ContainerDataRepository containerDataRepository;
    private final ContainerAttrRepository containerAttrRepository;
    private final OcrVerificationRepository ocrVerificationRepository;

    private final CoverOcrVerificationService ocrVerificationService;

    /**
     * 固定頻率巡檢：
     * - fixedDelay：每次 tick 完再等待 N ms
     * - initialDelay：啟動後延遲 N ms 才開始
     */
    @Scheduled(
            fixedDelayString = "${monitor.cover-ocr.fixed-delay-ms:500}",
            initialDelayString = "${monitor.cover-ocr.initial-delay-ms:2000}"
    )
    public void tick() {

        handlePair("Site#12", "Site#36", "PAIR_12_36");
        handlePair("Site#12", "Site#37", "PAIR_12_37");
//        handlePair("Site#12", "Site#25", "PAIR_12_25");
//        handlePair("Site#12", "Site#26", "PAIR_12_26");

        handlePair("Site#14", "Site#25", "PAIR_14_25");
        handlePair("Site#14", "Site#26", "PAIR_14_26");
        handlePair("Site#14", "Site#27", "PAIR_14_27");
//        handlePair("Site#14", "Site#36", "PAIR_14_36");
//        handlePair("Site#14", "Site#37", "PAIR_14_37");
//        handlePair("Site#14", "Transfer#8", "PAIR_14_TR8");
    }

    /**
     * 單組固定配對處理：
     *
     * @param refSite  固定 cover/ref 站（Site#12 or Site#14）
     * @param traySite 固定 tray/current 站（Site#36 or Site#25）
     * @param tag      log tag
     */
    private void handlePair(String refSite, String traySite, String tag) {

        // (0) tray/current 必須有容器
        Optional<Long> trayCmOpt = locationTrackingRepository.findContainerAtLocationName(traySite);
        if (trayCmOpt.isEmpty()) return;

        // (1) ref/cover 必須有容器
        Optional<Long> refCmOpt = locationTrackingRepository.findContainerAtLocationName(refSite);
        if (refCmOpt.isEmpty()) return;

        final Long trayCmId = trayCmOpt.get(); // current/tray（現場這顆）
        final Long refCmId = refCmOpt.get();  // ref/cover（參考這顆）

        // (2) 若有上蓋，後續就不需走判斷
        Integer trayCoverVal = getCoverLayersStrict(trayCmOpt.get());
        if (trayCoverVal > 0) return;
        ;

        // (3) 取得 Anchor（祖先 cmId）：用於「上下文 Key」
        final Long anchorCmId = resolveAnchorCmId(trayCmId);

        // (A) 檢查 anchor+ref 上是否已有 OcrVerification（避免拆批分身造成重複上下文）
        //     必須加 refCmId 條件：refContainer 可能被替換，不能拿舊 ref 的 context 繼續跑。
        Optional<OcrVerification> existing =
                ocrVerificationRepository.findLatestByContainerMainIdAndRefContainerId(anchorCmId, refCmId);

        if (existing.isPresent()) {
            FinalDecision d = ocrVerificationService.decideFinal(anchorCmId, existing.get());

            // 終態：不需要再推進、也不需要再算 decision
            if (d == FinalDecision.PASS || d == FinalDecision.BLOCK) {
                //log.debug("[COVER_OCR][{}] anchor#{} already terminal={} (ref#{}@{}) -> skip",
//                        tag, anchorCmId, d, refCmId, refSite);
                return;
            }

            // 非終態：允許往下走（可能要 retry S073 / 等人工）
            //log.debug("[COVER_OCR][{}] anchor#{} existing decision={} (ref#{}@{}) -> continue",
//                    tag, anchorCmId, d, refCmId, refSite);
        } else {
            log.info("[COVER_OCR][{}] tray={} cm#{} anchor#{} has NO OcrVerification(for ref#{}@{}) -> create & kick flow",
                    tag, traySite, trayCmId, anchorCmId, refCmId, refSite);
        }

        // (C) 固定配對 decision（料號必須一致；OCR 1對1、2對2，不混用）
        MatchDecision md = evaluateFixedPairDecision(trayCmId, refSite, refCmId);

        if (!md.partMatch || !md.ocrReady) {
            //log.debug("[COVER_OCR][{}] skip: partMatch={}, ocrReady={}", tag, md.partMatch, md.ocrReady);
            return;
        }

        // (D) 交給 service：以 anchorCmId 當上下文 key，並用 tray/current 的 OCR 快照做自判 + 發S073
        // 目前 upsert 使用 anchor 作為 container_main_id，避免拆批分身重複上下文。
        //     但快照來源（OCR/partNo）仍是 tray/current 與 ref/cover
        OcrVerification ctx = ocrVerificationService.upsertAutoSnapshotAndHandleS073(anchorCmId, md);

        // (E) 可選：看一下目前決策（只是 log，不做搬運）
        FinalDecision decision = ocrVerificationService.decideFinal(anchorCmId, ctx);
        //log.debug("[COVER_OCR][{}] tray cm#{} anchor#{} ref={} cm#{} decision={} (no DROP in this monitor)",
//                tag, trayCmId, anchorCmId, refSite, refCmId, decision);
    }

    /**
     * 固定配對站點的 decision：
     * - refSite 固定（Site#12 or Site#14）
     * - tray=current（Site#36 or Site#25）
     * - 料號必須一致才 partMatch=true
     * - OCR 比對固定 1對1、2對2（不混用）
     * <p>
     * 注意：本 decision 使用的是「現場 tray/current」與「現場 ref/cover」的資料。
     */
    private MatchDecision evaluateFixedPairDecision(Long trayCmId, String refSite, Long refCmId) {
        MatchDecision md = new MatchDecision();
        md.refSite = refSite;
        md.refCmId = refCmId;
        md.lane = "FIXED_PAIR";

        String trayPartNo = getPartNo(trayCmId);
        String refPartNo = getPartNo(refCmId);

        md.partMatch = strEq(trayPartNo, refPartNo);

        OcrPair trayOcr = getOcrPair(trayCmId);
        OcrPair refOcr = getOcrPair(refCmId);

        boolean trayReady = notBlank(trayOcr.t1) || notBlank(trayOcr.t2);
        boolean refReady = notBlank(refOcr.t1) || notBlank(refOcr.t2);

        md.ocrReady = trayReady && refReady;
        if (!md.ocrReady) {
            String ocrNotReadyReason = "trayReady=" + trayReady + ", refReady=" + refReady;
            //log.debug("[COVER_OCR] OCR not ready -> wait. cm#{} refSite={} refCmId={} {}",
//                    trayCmId, md.refSite, md.refCmId, ocrNotReadyReason);
            return md; // 不做 match
        }

        md.ocr1Match = strEq(trayOcr.t1, refOcr.t1);
        md.ocr2Match = strEq(trayOcr.t2, refOcr.t2);

        log.info("[COVER_OCR] fixed decision tray cm#{} → refSite={} ref cm#{} | partMatch={}, ocr1Match={}, ocr2Match={}",
                trayCmId, refSite, refCmId, md.partMatch, md.ocr1Match, md.ocr2Match);

        return md;
    }

    /**
     * 僅回傳 container_data.cover_layers（可能為 NULL 表示未知）
     */
    private Integer getCoverLayersStrict(Long containerMainId) {
        if (containerMainId == null) return null;
        ContainerData cd = containerDataRepository.findByContainerMainId(containerMainId).orElse(null);
        return (cd == null) ? null : cd.getCoverLayers();
    }

    /* =============================== Ancestor/Anchor =============================== */

    /**
     * 取得 tray/current 的祖先(Anchor) containerId：
     * - 優先用 LINEAGE_ROOT_CMID（若存在就直接當 anchor）
     * - 否則沿 LINEAGE_PARENT_CMID 往上追（最多追 N 層，避免循環/髒資料卡死）
     * - 都找不到 → 回傳自己
     * <p>
     * 這樣做的目的是：
     * - 拆批/分身會產生新 containerId，但它們應共享同一個 OCR 驗證上下文。
     */
    private Long resolveAnchorCmId(Long selfCmId) {
        if (selfCmId == null) return null;

        // 1) root 優先
        Optional<Long> rootOpt = readLongAttr(selfCmId, ATTR_ROOT);
        if (rootOpt.isPresent()) return rootOpt.get();

        // 2) 沿 parent 往上追
        Long cur = selfCmId;
        Set<Long> visited = new HashSet<>();
        visited.add(cur);

        for (int i = 0; i < 8; i++) { // 追 8 層足夠（避免異常資料無限追）
            Optional<Long> pOpt = readLongAttr(cur, ATTR_PARENT);
            if (pOpt.isEmpty()) break;

            Long p = pOpt.get();
            if (p == null || p <= 0) break;
            if (visited.contains(p)) break; // 防循環

            visited.add(p);
            cur = p;

            // 若上層有 root，優先用 root
            Optional<Long> pRoot = readLongAttr(cur, ATTR_ROOT);
            if (pRoot.isPresent()) return pRoot.get();
        }

        return cur; // 找不到 root，就用追到的最上層；如果完全沒 parent，就等於 self
    }

    private Optional<Long> readLongAttr(Long cmId, String key) {
        return containerAttrRepository.findOne(cmId, key)
                .map(ContainerAttr::getAttrValue)
                .flatMap(v -> {
                    try {
                        return Optional.of(Long.parseLong(v.trim()));
                    } catch (Exception ignore) {
                        return Optional.empty();
                    }
                });
    }

    /* =============================== helpers =============================== */

    private static final class OcrPair {
        final String t1; // back
        final String t2; // front

        OcrPair(String t1, String t2) {
            this.t1 = t1;
            this.t2 = t2;
        }
    }

    private boolean hasOcr(Long cmId) {
        return containerDataRepository.findByContainerMainId(cmId)
                .map(this::hasAnyOcr)
                .orElse(false);
    }

    private boolean hasAnyOcr(ContainerData cd) {
        return notBlank(cd.getOcrText1()) || notBlank(cd.getOcrText2());
    }

    private OcrPair getOcrPair(Long cmId) {
        return containerDataRepository.findByContainerMainId(cmId)
                .map(cd -> new OcrPair(safeTrim(cd.getOcrText1()), safeTrim(cd.getOcrText2())))
                .orElse(new OcrPair("", ""));
    }

    private String getPartNo(Long cmId) {
        return containerMainRepository.findById(cmId)
                .map(ContainerMain::getPartNo)
                .map(this::safeTrim)
                .orElse("");
    }

    private boolean strEq(String a, String b) {
        return safeTrim(a).equalsIgnoreCase(safeTrim(b));
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}