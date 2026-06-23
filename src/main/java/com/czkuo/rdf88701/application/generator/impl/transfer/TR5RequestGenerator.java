package com.czkuo.rdf88701.application.generator.impl.transfer;

import com.czkuo.rdf88701.application.generator.TransferRequestGenerator;
import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.cover.CoverLaneDecisionService;
import com.czkuo.rdf88701.application.service.cover.CoverLaneDecisionService.TrMode;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.common.enums.cover.CoverLane;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.entity.TransferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TR5RequestGenerator
 * ----------------------------------------------------------------------
 * - 控管 MAIN 線的 Transfer#5：在公蓋區 13/14/Transfer#5 之間搬 ALL_COVER。
 * <p>
 * 規則（只看 MAIN lane 的 R029 任務；完全對齊 CoverLaneDecisionService）
 * ----------------------------------------------------------------------
 * 1) 任務存在且 mismatch        → RECALL：14 → 13
 * 2) 任務存在且無 mismatch      → SUPPLY：13 → 14
 * 3) 沒任務                    → RECALL：14 → 13
 * 4) 有任務但 trayType 為空     → NONE：保守不搬（不做 13/14/TR5 的任何 PICK/DROP）
 * <p>
 * 其他約束：
 * - 三個位置線性，只允許一顆：
 * * SUPPLY：TR5 空手 & 13 有 & 14 空
 * * RECALL：TR5 空手 & 14 有 & 13 空
 * - OCR#2 若在 Site#14 → 禁止所有對 Site#14 的 MOVE/PICK/DROP
 * <p>
 * ★ 新增「補位優先」規則（你要求的重點）：
 * - 只要 MAIN lane 還有「可補位」需求（例如 Site#27 無蓋 且 OCR 與 Site#14 成對相同）
 * → 禁止 TR5 進行 RECALL（14→13），避免把 pool 的蓋載走
 * → 必須等補完後，才允許回收
 */
@Slf4j
@Component("TR5")
@RequiredArgsConstructor
public class TR5RequestGenerator implements TransferRequestGenerator {

    private final TransferRequestRepository requestRepository;
    private final TransferTaskRepository taskRepository;
    private final CraneRequestRepository craneRequestRepository;
    private final CraneTaskRepository craneTaskRepository;
    private final WorkingBeamRequestRepository workingBeamRequestRepository;
    private final WorkingBeamTaskRepository workingBeamTaskRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final TransferStatusCache transferStatusCache;
    private final PlcAccessService plc;
    private final InfraredRequestRepository infraredRequestRepository;
    private final InfraredTaskRepository infraredTaskRepository;
    private final DeviceProcessStateReader stateReader;
    /**
     * 共用 lane 路徑判斷（完全對齊 TR4/TR5 規則，避免規則漂移）
     */
    private final CoverLaneDecisionService coverLaneDecisionService;
    private static final int IDLE_RECLAIM_THRESHOLD = 60;
    private static int count = 0;


    // crane busy 判斷也是用 deviceId=1 / craneId="1"
    private static final Long CRANE_DEVICE_ID = 1L;
    private static final String CRANE_ID = "1";

    // Main(5L)/Sub(6L) lane beam id
    private static final long WB5_BEAM_ID = 5L;
    private static final long WB6_BEAM_ID = 6L;

    // 線性順序：Site#13 → Site#14
    private static final String FROM_SITE = "Site#13";  // staging
    private static final String TO_SITE = "Site#14";  // pool
    private static final Long FROM_ID = 85L;  // staging
    private static final Long TO_ID = 213L;  // pool
    private static final List<CoverLane> LANES = List.of(CoverLane.MAIN);

    // 站點 Level（依實機對應）
    private static final int TO_SITE_LEVEL = 14;

    // OCR#2 位址（與 Ocr2MotionMonitor 一致）
    private static final String PLC_DEVICE = "PLC-Packer";
    private static final String OCR2_W_POS_LEVEL = "W13C1"; // Transfer Device Level Position（站點）
    private static final long INFRARED_ID = 6L;

    @Override
    public Optional<Long> generateRequest(Long transferId) {
        if (!deviceIsRun(TransferGeneratorConstants.SPLIT_MERGE_AREA))
            return Optional.empty();

        // 0) 若已有未完成請求/任務 → 略過
        if (requestRepository.existsUnfinishedRequestForDevice(transferId)
                || taskRepository.existsUnfinishedTaskForTransfer(transferId)) {
            //log.debug("[TR5] Transfer#{} 已有未完成請求或任務，略過", transferId);
            return Optional.empty();
        }
        if (infraredBusy(INFRARED_ID)) {
            //log.debug("[GP2] 忙碌互斥（IR/Gripper），略過。");
            return Optional.empty();
        }

        // 1) 讀 OCR#2 Level（主要是為了擋 Site#14）
        Integer lv = readOcr2Level();
        if (lv == null) {
            //log.debug("[TR5] 讀取 OCR#2 位置失敗");
            return Optional.empty();
        }
        boolean ocrAt14 = isOcr2AtSite14(lv);

        // 2) 由 CoverLaneDecisionService 決定本輪路徑（SUPPLY / RECALL / NONE）
        TrMode mode = coverLaneDecisionService.resolveModeMain();

        String trayTypeUpper = coverLaneDecisionService.resolveTrayTypeUpper("MAIN");

        boolean modeSupply = (mode == TrMode.SUPPLY);
        boolean modeRecall = (mode == TrMode.RECALL);
        boolean modeNone = (mode == TrMode.NONE);

        if (modeNone) {
            //log.debug("[TR5] MAIN lane mode=NONE（trayType 可能為空），本輪不搬任何公蓋。 trayType={}", trayTypeUpper);
        } else {
            //log.debug("[TR5] MAIN lane mode={} trayType={}", mode, trayTypeUpper);
        }

        // 2.2) 補位優先 gate（只擋 RECALL）
        //      只要還有可補的，就必須等補完，TR5 不應把 pool 的蓋回收走。
        boolean pendingSupplyMain = false;
        if (modeRecall) {
            try {
                pendingSupplyMain = coverLaneDecisionService.hasPendingCoverSupplyMain();
            } catch (Exception e) {
                // 保守：判斷補位需求出錯 → 不要亂回收（避免載走蓋導致缺蓋）
                pendingSupplyMain = true;
                log.warn("[TR5] hasPendingCoverSupplyMain() failed, block RECALL for safety: {}", e.getMessage());
            }
            if (pendingSupplyMain) {
                //log.debug("[TR5] RECALL blocked: MAIN lane still has pending cover supply (wait until補完).");
            }
        }

        // 3) 現場位置狀態
        TR5Context local = new TR5Context();
        Optional<Long> site13Container = local.getContainerAtSite(FROM_SITE);
        Optional<Long> site14Container = local.getContainerAtSite(TO_SITE);
        boolean site13Has = site13Container.isPresent();
        boolean site14Has = site14Container.isPresent();

        Optional<Long> containerOnTransfer = local.getContainerOnTransfer(transferId);

        // 3.1) Crane 對 site13 是否已有「要取/要放」的 request/task
        boolean craneWantsSite13 = isCraneTouchingLocation(FROM_ID);
        if (craneWantsSite13) {
            //log.debug("[TR5] Crane intent detected: wantsSite13={} (block conflicting TR4 moves this round)", craneWantsSite13);
        }

        // =============================================================
        // A. Transfer#5 上已有容器 → 決定 DROP 方向
        // =============================================================
        if (containerOnTransfer.isPresent()) {
            Long containerId = containerOnTransfer.get();

            if (modeSupply) {
                // 13→14 供蓋：DROP 到 Site#14（前提：Site#14 為空且 OCR 不在 14）
                if (!site14Has) {
                    if (ocrAt14) {
                        //log.debug("[TR5] SUPPLY DROP→{}：但 OCR#2 在 Site#14，禁止 DROP@14。", TO_SITE);
                        return Optional.empty();
                    }
                    log.info("[TR5] SUPPLY：DROP container#{} → {}", containerId, TO_SITE);
                    return createRequest(transferId, "DROP", null, TO_SITE, containerId);
                } else {
                    //log.debug("[TR5] SUPPLY：{} 已有容器，無法 DROP。", TO_SITE);
                    return Optional.empty();
                }
            }

            if (modeRecall) {
                // 補位未完成：禁止回收（包含 drop@13）
                if (pendingSupplyMain) {
                    // 補位尚未完成 → 不回收，將蓋 DROP 回 pool（Site#14）
                    if (!site14Has) {
                        if (ocrAt14) {
                            //log.debug("[TR5] RECALL cancelled: pending supply, but OCR#2 at Site#14 → cannot DROP back to pool.");
                            return Optional.empty();
                        }
                        log.info("[TR5] RECALL cancelled → DROP container#{} back to pool {}", containerId, TO_SITE);
                        return createRequest(transferId, "DROP", null, TO_SITE, containerId);
                    } else {
                        //log.debug("[TR5] RECALL cancelled: pool {} already has container, cannot DROP back.", TO_SITE);
                        return Optional.empty();
                    }
                }

                // 14→13 回收：DROP 到 Site#13（前提：Site#13 為空）
                if (!site13Has) {
                    // 若 crane 牽涉到 Site#13，避免 DROP 搶點
                    if (craneWantsSite13) {
                        //log.debug("[TR5] RECALL DROP blocked: crane is touching {} (pick/place pending).", FROM_SITE);
                        return Optional.empty();
                    }

                    log.info("[TR5] RECALL：DROP container#{} → {}", containerId, FROM_SITE);
                    return createRequest(transferId, "DROP", null, FROM_SITE, containerId);
                } else {
                    //log.debug("[TR5] RECALL：{} 已有容器，無法 DROP。", FROM_SITE);
                    return Optional.empty();
                }
            }

            // modeNone：保守不搬
            //log.debug("[TR5] Transfer#{} 上有容器，但 mode=NONE（trayType 可能為空）→ 本輪略過。 containerId={}",
//                    transferId, containerId);
            return Optional.empty();
        }

        // =============================================================
        // B. Transfer#5 空手 → 決定 PICK 方向
        // =============================================================
        if (modeSupply) {
            // 供蓋模式（13→14）：
            // 條件：Site#13 有、Site#14 無
            if (site13Has && !site14Has) {
                if (ocrAt14) {
                    //log.debug("[TR5] SUPPLY：原計畫 PICK {} → {}，但 OCR#2 在 Site#14，禁止經過 14。",
//                            FROM_SITE, TO_SITE);
                    return Optional.empty();
                }

                // 若 crane 需要 @13（取），TR5 本輪不要碰 @13
                if (craneWantsSite13) {
                    //log.debug("[TR5] SUPPLY PICK blocked: crane is touching {} (pick/place pending).", FROM_SITE);
                    return Optional.empty();
                }

                Long cid = site13Container.orElse(null);
                if (cid != null) {
                    log.info("[TR5] SUPPLY：PICK {} → {} (container#{})", FROM_SITE, TO_SITE, cid);
                    return createRequest(transferId, "PICK", FROM_SITE, TO_SITE, cid);
                }
            }
        } else if (modeRecall) {
            // 補位未完成：禁止回收（包含 pick@14）
            if (pendingSupplyMain) {
                //log.debug("[TR5] RECALL PICK blocked: pending cover supply not finished yet.");
                return Optional.empty();
            }

            // 回收模式（14→13；沒任務也屬於回收）：
            // 條件：Site#14 有、Site#13 無
            if (site14Has && !site13Has) {
                if (ocrAt14) {
                    //log.debug("[TR5] RECALL：原計畫 PICK {} → {}，但 OCR#2 在 Site#14，暫不動作。",
//                            TO_SITE, FROM_SITE);
                    return Optional.empty();
                }
                if (workingBeamBusy(WB5_BEAM_ID)) {
                    //log.debug("[TR5] RECALL：原計畫 PICK {} → {}，但 WB#5 正在動作中，暫不動作。",
//                            TO_SITE, FROM_SITE);
                    return Optional.empty();
                }
                if (workingBeamBusy(WB6_BEAM_ID)) {
                    //log.debug("[TR5] RECALL：原計畫 PICK {} → {}，但 WB#6 正在動作中，暫不動作。",
//                            TO_SITE, FROM_SITE);
                    return Optional.empty();
                }
                Long cid = site14Container.orElse(null);
                if (cid != null) {
                    count++;
                    if (count == 1) {
                        log.info("[Cover] Lane {} enter idle reclaim countdown", CoverLane.MAIN);
                    }
                    if (count < IDLE_RECLAIM_THRESHOLD) {
                        return Optional.empty();
                    }
                    log.info("[TR5] RECALL：PICK {} → {} (container#{})", TO_SITE, FROM_SITE, cid);
                    count = 0;
                    return createRequest(transferId, "PICK", TO_SITE, FROM_SITE, cid);
                }
            }
        } else {
            //log.debug("[TR5] mode=NONE → 不建立 PICK（trayType 可能為空）。");
        }

        // =============================================================
        // C. 待命 MOVE：沒有載貨、也沒有要搬 → MOVE 到 Site#14 待命
        // =============================================================
        Integer curLevel = local.currentLevel(transferId);
        if (curLevel != null && curLevel != TO_SITE_LEVEL) {
            if (ocrAt14) {
                //log.debug("[TR5] 待命 MOVE：原計畫 MOVE 到 {}，但 OCR#2 在 Site#14，禁止 MOVE@14。", TO_SITE);
                return Optional.empty();
            }
            log.info("[TR5] 空閒待命：目前 Level={}，建立 MOVE → {} (Level={})", curLevel, TO_SITE, TO_SITE_LEVEL);
            return createRequest(transferId, "MOVE", null, TO_SITE, null);
        }

        //log.debug("[TR5] 無可搬移容器或已在待命站位，略過 Transfer#{}", transferId);
        return Optional.empty();
    }

    /**
     * crane 是否正在「要取/要放」該 location（包含：未 accepted request + 未完成 task）
     */
    private boolean isCraneTouchingLocation(Long locationId) {
        if (locationId == null) return false;

        // Request（未 accepted）
        boolean reqPick = craneRequestRepository.existsUnfinishedRequestPickFromLocation(CRANE_DEVICE_ID, locationId);
        boolean reqPlace = craneRequestRepository.existsUnfinishedRequestPlaceToLocation(CRANE_DEVICE_ID, locationId);

        // Task（未完成）
        boolean taskPick = craneTaskRepository.existsUnfinishedTaskPickFromLocation(CRANE_ID, locationId);
        boolean taskPlace = craneTaskRepository.existsUnfinishedTaskPlaceToLocation(CRANE_ID, locationId);

        return reqPick || reqPlace || taskPick || taskPlace;
    }

    // =====================================================================
    // 建立 TransferRequest
    // =====================================================================

    /**
     * 建立 TransferRequest 請求（MOVE 時 containerMainId 可為 null）
     */
    private Optional<Long> createRequest(Long transferId, String taskType,
                                         String source, String target, Long containerMainId) {
        Long sourceId = (source != null) ? locationId(source) : null;

        Long targetId = (target != null) ? locationId(target) : null;

        TransferRequest request = new TransferRequest();
        request.setRequestKey(UUID.randomUUID().toString());
        request.setVersion(1);
        request.setRequestSource("SYSTEM");
        request.setTransferId(transferId);
        request.setTaskType(taskType); // "PICK" / "DROP" / "MOVE"
        request.setAccepted("N");
        request.setRequestTime(LocalDateTime.now());
        request.setCreatedTime(LocalDateTime.now());
        request.setSourceLocationId(sourceId);
        request.setTargetLocationId(targetId);
        request.setSourceLocationName(source);
        request.setTargetLocationName(target);
        request.setContainerMainId(containerMainId);

        boolean success = requestRepository.save(request);
        if (success) {
            log.info("[TR5] 建立 TransferRequest 成功: {} → {} [{}], containerId={}",
                    source, target, taskType, containerMainId);
            return Optional.of(request.getId());
        } else {
            log.warn("[TR5] 建立 TransferRequest 失敗 [{}]", taskType);
            return Optional.empty();
        }
    }

    // =====================================================================
    // 裝置 / OCR 相關
    // =====================================================================

    /**
     * 取得 TR#5 的目前 Level（cache 無效則回 null）
     */
    private Integer currentLevel(Long transferId) {
        try {
            TransferDeviceStatus ds = transferStatusCache.getLatest("Transfer#" + transferId);
            if (ds == null || !ds.isValidAndComplete(3)) return null;
            return ds.getLevel();
        } catch (Throwable ignore) {
            return null;
        }
    }

    /**
     * 讀取 OCR#2 的 Level；讀不到時回 null
     */
    private Integer readOcr2Level() {
        try {
            return plc.readInt16(PLC_DEVICE, OCR2_W_POS_LEVEL);
        } catch (Exception e) {
            log.warn("[TR5] 讀取 OCR#2 位置失敗：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 只要 OCR#2 位於 Site#14，就禁止 MOVE/PICK/DROP 涉及 Site#14
     */
    private boolean isOcr2AtSite14(Integer lv) {
        boolean at = (lv != null && lv == TO_SITE_LEVEL);
        if (at) {
            //log.debug("[TR5] OCR#2 目前在 Site#14(Level={}) → 對 Site#14 的 MOVE/PICK/DROP 禁止。", lv);
        }
        return at;
    }

    /**
     * 指定工作樑裝置是否忙碌（有未完成請求或任務）
     */
    private boolean workingBeamBusy(long workingBeamId) {
        return workingBeamRequestRepository.existsUnfinishedRequestForBeam(workingBeamId)
                || workingBeamTaskRepository.existsUnfinishedTaskForBeam(workingBeamId);
    }

    /**
     * 指定紅外線裝置是否忙碌（有未完成請求或任務）
     */
    private boolean infraredBusy(long infraredId) {
        return infraredRequestRepository.existsUnfinishedRequestForInfrared(infraredId)
                || infraredTaskRepository.existsUnfinishedTaskForInfrared(infraredId);
    }
    private boolean deviceIsRun(String deviceName) {
        return stateReader.getBestEffort(deviceName).getStatus() != ProcessStatus.STOP;
    }

    private Long locationId(String name) {
        return TransferLocationCache.requireLocationId(locationPointRepository, name);
    }

    private class TR5Context {
        private final Map<String, Optional<Long>> containerBySite = new ConcurrentHashMap<>();
        private final Map<Long, Optional<Long>> containerByTransfer = new ConcurrentHashMap<>();
        private final Map<Long, Integer> levelByTransfer = new ConcurrentHashMap<>();

        Optional<Long> getContainerAtSite(String siteName) {
            return containerBySite.computeIfAbsent(siteName, locationTrackingRepository::findContainerAtLocationName);
        }

        Optional<Long> getContainerOnTransfer(Long transferId) {
            return containerByTransfer.computeIfAbsent(transferId, locationTrackingRepository::findContainerOnTransfer);
        }

        Integer currentLevel(Long transferId) {
            return levelByTransfer.computeIfAbsent(transferId, TR5RequestGenerator.this::currentLevel);
        }
    }
}
