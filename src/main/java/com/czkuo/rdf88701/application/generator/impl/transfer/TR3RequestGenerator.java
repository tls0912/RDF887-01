package com.czkuo.rdf88701.application.generator.impl.transfer;

import com.czkuo.rdf88701.application.generator.TransferRequestGenerator;
import com.czkuo.rdf88701.application.service.cover.CoverOcrVerificationService;
import com.czkuo.rdf88701.application.service.cover.CoverOcrVerificationService.*;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * TR3RequestGenerator
 * -------------------------------------------------------------------
 * - Transfer#3：Site#9 → Site#10
 * <p>
 * 規則（修正版，與 service 實作一致）：
 * <p>
 * 1) 無 OCR → MOVE 到 VIRTUAL#5
 * <p>
 * 2) 有 OCR → 與上蓋站比對（Site#12 / Site#14）：
 *    - 先決定 MAIN/SUB lane（依 carrierId 回溯 R029 open task 的 lane）
 *    - lane=MAIN：只跟 Site#14 比（上蓋站固定 Site#14）
 *    - lane=SUB ：只跟 Site#12 比（上蓋站固定 Site#12）
 *    - lane unknown：fallback（兩邊都可比；兩邊都中優先 Site#12）
 *    - 【送 S073 的門檻】：
 *        * 料號必須一致（partMatch=true）
 *        * refSite 必須可決定（refSite != blank）
 *      才進入 OCR 驗證流程（upsert + 送 S073 + 等結果）
 * <p>
 * 3) 放行 DROP 決策（自判 > S073 > 人判，但「一定要等 S073 完成」）：
 *    - 進入 OCR 驗證後：必須等 S073 狀態變成 PASS/FAIL/ERROR 才能做最終決策
 *    - 自判 PASS（localPass=Y）：最終 PASS（但仍需等 S073 完成才輸出 PASS）
 *    - 自判 FAIL + S073 PASS：最終 PASS（並矯正 OCR）
 *    - 自判 FAIL + S073 FAIL/ERROR：進人工（PENDING → ALLOW/BLOCK）
 *    - 人工 BLOCK 優先權最高：一律不放行
 * <p>
 * 4) Site#10 有空位才 DROP
 */
@Slf4j
@Component("TR3")
@RequiredArgsConstructor
public class TR3RequestGenerator implements TransferRequestGenerator {

    // ===== 搬運請求/任務 =====
    private final TransferRequestRepository requestRepository;
    private final TransferTaskRepository taskRepository;

    // ===== 位置/追蹤 =====
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;

    // ===== Transfer 狀態快取（Level 判斷用，避免在 OCR 區亂 MOVE） =====
    private final TransferStatusCache statusCache;

    // ===== 產品主表 =====
    private final ContainerMainRepository containerMainRepository;

    // ===== OCR 文字仍存放在 container_data（用於判斷是否有 OCR）=====
    private final ContainerDataRepository containerDataRepository;

    // ===== OCR 驗證共用服務（自判 / S073 / 人判 / 最終矯正）=====
    private final CoverOcrVerificationService ocrVerificationService;

    // ===== R029 資料來源（lane 解析用）=====
    private final RobotR029TaskRepository r029TaskRepository;
    private final RobotInR029LotRepository inR029LotRepository;

    // ===== 常數 =====
    private static final String VIRTUAL_5 = "VIRTUAL#5";
    private final DeviceProcessStateReader stateReader;

    /**
     * OCR Level（防呆）
     * - 若 TR3 目前 Level=OCR_LEVEL，代表設備認為還在 OCR 區，暫不 MOVE 無 OCR 的料
     * - 這個值維持你原本的設計
     */
    private static final int OCR_LEVEL = 205;

    /** TR3 上蓋站位（固定）：SUB→Site#12；MAIN→Site#14 */
    private static final String REF_SITE_A = "Site#12";
    private static final String REF_SITE_B = "Site#14";

    @Override
    public Optional<Long> generateRequest(Long transferId) {
        if (!deviceIsRun(TransferGeneratorConstants.SPLIT_MERGE_AREA))
            return Optional.empty();

        // 1) 已有未完成請求/任務 → 略過
        if (requestRepository.existsUnfinishedRequestForDevice(transferId)
                || taskRepository.existsUnfinishedTaskForTransfer(transferId)) {
            //log.debug("[TR3] Transfer#{} 已有未完成請求或任務，略過", transferId);
            return Optional.empty();
        }

        // 2) Transfer 上是否有容器
        TR3Context local = new TR3Context();
        Optional<Long> cmOnTrans = local.getContainerOnTransfer(transferId);
        if (cmOnTrans.isPresent()) {
            Long cmId = cmOnTrans.get();

            // 2-1) 無 OCR → 送去 V#5（但 OCR_LEVEL 防呆）
            if (!hasOcr(cmId, local)) {
                String transferName = "Transfer#" + transferId;
                TransferDeviceStatus ds = statusCache.getLatest(transferName);
                boolean fresh = ds != null && ds.isValidAndComplete(3);
                if (!fresh) return Optional.empty();

                Integer level = safeGetLevel(ds);
                if (level != null && level == OCR_LEVEL) {
                    //log.debug("[TR3] Transfer#{} Level={} (OCR 區)，暫不 MOVE 無 OCR 料到 {}", transferId, level, VIRTUAL_5);
                    return Optional.empty();
                }

                log.info("[TR3] cm#{} 無 OCR → MOVE {}", cmId, VIRTUAL_5);
                return createRequest(transferId, "MOVE", null, VIRTUAL_5, cmId);
            }

            // 2-2) 有 OCR → 先做 lane/ref/料號/OCR 比對決策（不送訊息）
            MatchDecision md = evaluateS073Decision(cmId, local);

            // 料號要一致 + refSite 不可空 + OCR 值準備好，才進 OCR 驗證與 S073 流程
            if (!md.partMatch || isBlank(md.refSite) || !md.ocrReady) {
                log.info("[TR3] cm#{} skip S073: partMatch={}, refSite='{}', ocrReady={} (lane={})",
                        cmId, md.partMatch, safeTrim(md.refSite), md.ocrReady, safeTrim(md.lane));
                // 這裡保守不放行、也不做人工流程（因為要求 refSite 不能空、料號要一致、OCR 值準備好）
                return Optional.empty();
            }

            // 更新 / 建立 OCR 驗證紀錄，並處理 S073（含冷卻 / 等結果）
            OcrVerification ctx = ocrVerificationService.upsertAutoSnapshotAndHandleS073(cmId, md);

            // 2-3) 等 service 給最終決策（PASS / WAIT_S073 / NEED_MANUAL / WAIT_MANUAL / BLOCK）
            return decideDropWithVerification(transferId, cmId, ctx, local);
        }

        // 3) Transfer 無容器：Site#9 有且 Site#10 空 → PICK Site#9 → Site#10
        Optional<Long> cmAt9 = local.getContainerAtSite("Site#9");
        if (cmAt9.isPresent() && local.getContainerAtSite("Site#10").isEmpty()) {
            return createRequest(transferId, "PICK", "Site#9", "Site#10", cmAt9.get());
        }

        //log.debug("[TR3] 無可搬移容器或空位，略過 Transfer#{}", transferId);
        return Optional.empty();
    }

    /* =============================== R029：lane 解析（決定要比對 Site#12 或 Site#14） =============================== */

    /**
     * lane 解析邏輯：
     * - 用 current container 的 carrierId 去找「open R029」中哪個任務的 lot 清單包含此 carrierId
     * - 優先 internal_state=PROCESSING，其次任取第一筆符合
     * - 回傳 ctx.getLane()（預期為 "MAIN" 或 "SUB"；其他視為未知）
     */
    private Optional<String> resolveLaneByCarrier(String carrierIdRaw) {
        String key = norm(carrierIdRaw);

        List<RobotR029Task> open = safeList(r029TaskRepository.findOpen());
        if (open.isEmpty()) return Optional.empty();

        List<RobotR029Task> matched = new ArrayList<>();
        for (RobotR029Task t : open) {
            Long logId = t.getLogId();
            if (logId == null) continue;

            List<String> carriers = safeList(inR029LotRepository.findCarrierIdsByLogId(logId));
            boolean hit = carriers.stream()
                    .filter(Objects::nonNull)
                    .map(this::norm)
                    .anyMatch(key::equals);

            if (hit) matched.add(t);
        }
        if (matched.isEmpty()) return Optional.empty();

        RobotR029Task ctx = matched.stream()
                .filter(x -> "PROCESSING".equalsIgnoreCase(safe(x.getInternalState())))
                .findFirst()
                .orElse(matched.get(0));

        String lane = safe(ctx.getLane()).trim().toUpperCase(Locale.ROOT);
        return lane.isEmpty() ? Optional.empty() : Optional.of(lane);
    }

    /* =============================== S073：比對決策（不送訊息） =============================== */

    /**
     * 規則：
     * 1) 先決定 lane（由 carrierId 回溯 open R029）
     * 2) 依 lane 選 refSite：
     *    - MAIN → Site#14
     *    - SUB  → Site#12
     *    - UNKNOWN → fallback：兩邊都可比，兩邊都中優先 Site#12
     * 3) 料號一致才算 partMatch=true；且 refSite/refCmId 必須可取得
     * 4) OCR 比對固定 1對1、2對2（不混用）
     */
    private MatchDecision evaluateS073Decision(Long currentCmId, TR3Context local) {
        MatchDecision md = new MatchDecision();

        String currentPartNo = getTrayTypeByContainerId(currentCmId, local);
        if (isBlank(currentPartNo)) {
            md.lane = "UNKNOWN";
            //log.debug("[TR3] cm#{} 無 part_no，無法比對上蓋站", currentCmId);
            return md;
        }

        // (A) lane
        String carrierId = getCarrierIdByContainerId(currentCmId, local);
        Optional<String> laneOpt = notBlank(carrierId) ? resolveLaneByCarrier(carrierId) : Optional.empty();
        String lane = laneOpt.orElse("UNKNOWN");
        md.lane = lane;

        // (B) 取得上蓋站位的容器
        Optional<Long> cmAt12 = local.getContainerAtSite(REF_SITE_A);
        Optional<Long> cmAt14 = local.getContainerAtSite(REF_SITE_B);

        // (C) 依 lane 決定 ref（但「必須料號一致」才成立）
        if ("MAIN".equalsIgnoreCase(lane)) {
            boolean pn14 = cmAt14.map(id -> getTrayTypeByContainerId(id, local))
                    .map(pn -> strEq(pn, currentPartNo)).orElse(false);

            if (!pn14) {
                log.info("[TR3] lane=MAIN → Site#14 無上蓋或料號不一致，拒絕進入 S073 (cm#{}, partNo={})",
                        currentCmId, currentPartNo);
                return md;
            }

            md.refCmId = cmAt14.orElse(null);
            md.refSite = REF_SITE_B;
            md.partMatch = true;

        } else if ("SUB".equalsIgnoreCase(lane)) {
            boolean pn12 = cmAt12.map(id -> getTrayTypeByContainerId(id, local))
                    .map(pn -> strEq(pn, currentPartNo)).orElse(false);

            if (!pn12) {
                log.info("[TR3] lane=SUB → Site#12 無上蓋或料號不一致，拒絕進入 S073 (cm#{}, partNo={})",
                        currentCmId, currentPartNo);
                return md;
            }

            md.refCmId = cmAt12.orElse(null);
            md.refSite = REF_SITE_A;
            md.partMatch = true;

        } else {
            boolean pn12 = cmAt12.map(id -> getTrayTypeByContainerId(id, local))
                    .map(pn -> strEq(pn, currentPartNo)).orElse(false);
            boolean pn14 = cmAt14.map(id -> getTrayTypeByContainerId(id, local))
                    .map(pn -> strEq(pn, currentPartNo)).orElse(false);

            if (pn12) {
                md.refCmId = cmAt12.orElse(null);
                md.refSite = REF_SITE_A;
                md.partMatch = true;
            } else if (pn14) {
                md.refCmId = cmAt14.orElse(null);
                md.refSite = REF_SITE_B;
                md.partMatch = true;
            } else {
                log.info("[TR3] lane=UNKNOWN → 兩站料號皆不一致，拒絕進入 S073 (cm#{}, partNo={})",
                        currentCmId, currentPartNo);
                return md;
            }
        }

        // (D) OCR 比對前：確保 OCR 有值（tray/ref 都要 ready）
        OcrPair curr = getOcrPair(currentCmId, local);
        OcrPair ref  = (md.refCmId != null) ? getOcrPair(md.refCmId, local) : new OcrPair("", "");

        boolean currReady = notBlank(curr.t1) || notBlank(curr.t2);
        boolean refReady  = notBlank(ref.t1)  || notBlank(ref.t2);

        md.ocrReady = currReady && refReady;
        if (!md.ocrReady) {
            String ocrNotReadyReason = "currReady=" + currReady + ", refReady=" + refReady;
            //log.debug("[TR3] OCR not ready -> wait. cm#{} refSite={} refCmId={} {}",
//                    currentCmId, md.refSite, md.refCmId, ocrNotReadyReason);
            return md;
        }

        // OCR 1對1、2對2
        md.ocr1Match = strEq(curr.t1, ref.t1);
        md.ocr2Match = strEq(curr.t2, ref.t2);

        log.info("[TR3] S073 candidate(lane={}) cm#{} → refSite={} refCmId={} | partMatch={}, ocr1Match={}, ocr2Match={}",
                lane, currentCmId, md.refSite, md.refCmId, md.partMatch, md.ocr1Match, md.ocr2Match);

        return md;
    }

    // =============================== 自判 > S073 > 人判（由 service 輸出 FinalDecision） ===============================

    private Optional<Long> decideDropWithVerification(Long transferId,
                                                      Long cmId,
                                                      OcrVerification ctx,
                                                      TR3Context local) {

        FinalDecision decision = ocrVerificationService.decideFinal(cmId, ctx);

        if (decision == FinalDecision.PASS) {
            if (local.getContainerAtSite("Site#10").isEmpty()) {
                log.info("[TR3] cm#{} PASS → DROP Site#10", cmId);
                return createRequest(transferId, "DROP", null, "Site#10", cmId);
            }
            //log.debug("[TR3] cm#{} PASS，但 Site#10 已滿，暫不 DROP", cmId);
            return Optional.empty();
        }

        // WAIT_S073 / NEED_MANUAL / WAIT_MANUAL / BLOCK：都不放行
        //log.debug("[TR3] cm#{} decision={} → 不放行 DROP", cmId, decision);
        return Optional.empty();
    }

    /* ================================= 原有流程/DB 取值 ================================= */

    private Integer safeGetLevel(TransferDeviceStatus ds) {
        try {
            return ds.getLevel();
        } catch (Throwable ignore) {
            return null;
        }
    }

    private Optional<Long> createRequest(Long transferId, String taskType, String source, String target, Long containerMainId) {
        Long sourceId = source != null ? locationId(source) : null;

        Long targetId = target != null ? locationId(target) : null;

        TransferRequest req = new TransferRequest();
        req.setRequestKey(UUID.randomUUID().toString());
        req.setVersion(1);
        req.setRequestSource("SYSTEM");
        req.setTransferId(transferId);
        req.setTaskType(taskType);
        req.setAccepted("N");
        req.setRequestTime(LocalDateTime.now());
        req.setCreatedTime(LocalDateTime.now());
        req.setSourceLocationId(sourceId);
        req.setTargetLocationId(targetId);
        req.setSourceLocationName(source);
        req.setTargetLocationName(target);
        req.setContainerMainId(containerMainId);

        boolean ok = requestRepository.save(req);
        if (ok) {
            log.info("[TR3] 建立 TransferRequest 成功: {} → {} [{}], cmId={}", source, target, taskType, containerMainId);
            return Optional.of(req.getId());
        }

        log.warn("[TR3] 建立 TransferRequest 失敗 [{}]", taskType);
        return Optional.empty();
    }

    /** OCR 是否已存在：兩欄任一有值即算有 */
    private boolean hasOcr(Long containerMainId, TR3Context local) {
        return local.getContainerData(containerMainId)
                .map(this::hasAnyOcr)
                .orElse(false);
    }

    private boolean hasAnyOcr(ContainerData cd) {
        return notBlank(cd.getOcrText1()) || notBlank(cd.getOcrText2());
    }

    /** 取得容器名稱來自 container_main.alias_code（必要） */
    private String getCarrierIdByContainerId(Long containerMainId, TR3Context local) {
        return local.getContainerMain(containerMainId)
                .map(ContainerMain::getAliasCode)
                .map(s -> s == null ? "" : s.trim())
                .orElse("");
    }

    /** 料號來自 container_main.part_no（必要） */
    private String getTrayTypeByContainerId(Long containerMainId, TR3Context local) {
        return local.getContainerMain(containerMainId)
                .map(ContainerMain::getPartNo)
                .filter(this::notBlank)
                .orElse(null);
    }

    /* =============================== OCR 字串比對 helpers =============================== */

    private static final class OcrPair {
        final String t1; // back
        final String t2; // front
        OcrPair(String t1, String t2) { this.t1 = t1; this.t2 = t2; }
    }

    /** 取容器的 OCR 文字（null→""；trim） */
    private OcrPair getOcrPair(Long containerMainId, TR3Context local) {
        return local.getContainerData(containerMainId)
                .map(cd -> new OcrPair(safeTrim(cd.getOcrText1()), safeTrim(cd.getOcrText2())))
                .orElse(new OcrPair("", ""));
    }

    /** null/空白 安全大小寫不敏感比較 */
    private boolean strEq(String a, String b) {
        return safeTrim(a).equalsIgnoreCase(safeTrim(b));
    }

    private String safeTrim(String s) { return s == null ? "" : s.trim(); }

    private boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    /* =============================== Lane 解析 helper（字串/集合安全） =============================== */

    private String norm(String s) { return safe(s).trim().toUpperCase(Locale.ROOT); }

    private static String safe(String s) { return (s == null) ? "" : s; }

    private static <T> List<T> safeList(List<T> list) { return (list == null) ? Collections.emptyList() : list; }

    private boolean deviceIsRun(String deviceName) {
        return stateReader.getBestEffort(deviceName).getStatus() != ProcessStatus.STOP;
    }

    private Long locationId(String name) {
        return TransferLocationCache.requireLocationId(locationPointRepository, name);
    }

    private class TR3Context {
        private final Map<Long, Optional<Long>> containerByTransfer = new HashMap<>();
        private final Map<String, Optional<Long>> containerBySite = new HashMap<>();
        private final Map<Long, Optional<ContainerMain>> containerMainById = new HashMap<>();
        private final Map<Long, Optional<ContainerData>> containerDataById = new HashMap<>();

        Optional<Long> getContainerOnTransfer(Long transferId) {
            return containerByTransfer.computeIfAbsent(transferId, locationTrackingRepository::findContainerOnTransfer);
        }

        Optional<Long> getContainerAtSite(String siteName) {
            return containerBySite.computeIfAbsent(siteName, locationTrackingRepository::findContainerAtLocationName);
        }

        Optional<ContainerMain> getContainerMain(Long containerMainId) {
            if (containerMainId == null) {
                return Optional.empty();
            }
            return containerMainById.computeIfAbsent(containerMainId, containerMainRepository::findById);
        }

        Optional<ContainerData> getContainerData(Long containerMainId) {
            if (containerMainId == null) {
                return Optional.empty();
            }
            return containerDataById.computeIfAbsent(containerMainId, containerDataRepository::findByContainerMainId);
        }
    }
}
