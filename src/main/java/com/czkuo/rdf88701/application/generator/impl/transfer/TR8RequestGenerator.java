package com.czkuo.rdf88701.application.generator.impl.transfer;


import com.czkuo.rdf88701.application.generator.TransferRequestGenerator;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.entity.TransferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * TR8RequestGenerator
 *
 * 規則（優先序）：
 * 1) Transfer#8 有容器 → MOVE → VIRTUAL#13（※ 若目前在 VIRTUAL#12，出發前需確認容器「有上蓋」）
 * 2) Site#37 有容器 → MOVE → VIRTUAL#12
 * 3) Site#22 有容器 → MOVE → VIRTUAL#14
 * 4) 其餘狀況 → 待命（MOVE → VIRTUAL#12）
 *
 * 其他：
 * - 若 Transfer#8 已有未完成請求/任務 → 不建單
 * - 先確認當前物理位置（透過 TransferStatusCache）；快取無效則不建單
 * - 若已在目標點位，則不建單
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component("TR8")
@RequiredArgsConstructor
public class TR8RequestGenerator implements TransferRequestGenerator {

    private final TransferRequestRepository transferRequestRepository;
    private final TransferTaskRepository transferTaskRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final TransferStatusCache statusCache;
    private final DeviceProcessStateReader stateReader;

    // 檢查容器 cover_layers（是否有上蓋）
    private final ContainerDataRepository containerDataRepository;

    // 檢查容器是否要確認有上蓋
    private final ContainerAttrRepository containerAttrRepository;

    private static final String VIRTUAL_12 = "VIRTUAL#12"; // 待命位
    private static final String VIRTUAL_13 = "VIRTUAL#13";
    private static final String VIRTUAL_14 = "VIRTUAL#14";

    private static final String SITE_37 = "Site#37";
    private static final String SITE_22 = "Site#22";

    // 依 PLC 對應調整：VIRTUAL#12/13/14 的實際 Level
    private static final int LEVEL_V12 = 212;
    private static final int LEVEL_V13 = 213;
    private static final int LEVEL_V14 = 214;

    // === 上蓋檢查屬性鍵 ===
    private static final String ATTR_NEED_COVER_CHECK = "NEED_COVER_CHECK";

    @Override
    public Optional<Long> generateRequest(Long transferId) {
        if (!deviceIsRun(TransferGeneratorConstants.SPLIT_MERGE_AREA))
            return Optional.empty();

        // 0) 該 Transfer 是否已有未完成請求/任務 → 若有則略過
        if (transferRequestRepository.existsUnfinishedRequestForDevice(transferId)
                || transferTaskRepository.existsUnfinishedTaskForTransfer(transferId)) {
            //log.debug("[TR8] Transfer#{} 已有未完成請求或任務，略過", transferId);
            return Optional.empty();
        }

        // 1) 取得目前是否載貨（容器是否在這台 Transfer 上）
        Optional<Long> containerOnTransfer = locationTrackingRepository.findContainerOnTransfer(transferId);
        boolean hasContainer = containerOnTransfer.isPresent();

        // 2) 從快取取得當前物理位置（Level），若無效則不建請求
        String transferName = "Transfer#" + transferId;
        TransferDeviceStatus ds = statusCache.getLatest(transferName);
        boolean fresh = ds != null && ds.isValidAndComplete(3);
        if (!fresh) {
            //log.debug("[TR8] Transfer#{} 狀態快取無效（ds==null 或 !isValidAndComplete(3)），此次不建請求", transferId);
            return Optional.empty();
        }

        Integer level = safeGetLevel(ds);
        boolean atV12 = level != null && level == LEVEL_V12;
        boolean atV13 = level != null && level == LEVEL_V13;
        boolean atV14 = level != null && level == LEVEL_V14;

        // 3) 查詢站位是否有帳
        boolean site37Has = locationTrackingRepository.findContainerAtLocationName(SITE_37).isPresent();
        boolean site22Has = locationTrackingRepository.findContainerAtLocationName(SITE_22).isPresent();

        // 4) 決策（有容器優先，其次 Site#37，再來 Site#22，最後回待命）
        if (hasContainer) {
            Long cid = containerOnTransfer.get();
            boolean needCheck = needCoverCheck(cid);

            // snapshot：決策前狀態一次打清楚
            //log.debug("[TR8] snapshot: Transfer#{} 有帳, cid={}, level={}, atV12={}, atV13={}, atV14={}, needCheck={}",
//                    transferId, cid, level, atV12, atV13, atV14, needCheck);

            // 需要確認上蓋：依 cover_layers 決定要在 12 還是 13
            if (needCheck) {
                Integer cover = getCoverLayersStrict(cid); // NULL=未知 → 視同沒有上蓋
                boolean hasCover = (cover != null && cover >= 1);

                if (hasCover) {
                    // 有上蓋 → 應該在 13
                    // clearCoverCheckFlag(cid); // 檢查通過後清掉 flag（之後視為一般貨）

                    if (!atV13) {
                        log.info("[TR8] Transfer#{} 有帳且需確認上蓋，已確認有上蓋 (cover_layers={})，MOVE → {}",
                                transferId, cover, VIRTUAL_13);
                        return createMove(transferId, VIRTUAL_13, cid);
                    }

                    //log.debug("[TR8] Transfer#{} 有帳且需確認上蓋，已在 {} 且 cover_layers={}，不建單",
//                            transferId, VIRTUAL_13, cover);
                    return Optional.empty();
                } else {
                    // 沒上蓋（或未知） → 應該在 12 讓夾爪補上蓋
                    if (!atV12) {
                        log.info("[TR8] Transfer#{} 有帳且需確認上蓋，但目前無上蓋 (cover_layers={})，MOVE → {} 等待補上蓋",
                                transferId, cover, VIRTUAL_12);
                        return createMove(transferId, VIRTUAL_12, cid);
                    }

                    //log.debug("[TR8] Transfer#{} 有帳且需確認上蓋，無上蓋且已在 {}，不建單（等待夾爪補上蓋）",
//                            transferId, VIRTUAL_12);
                    return Optional.empty();
                }
            }

            // 不需要確認上蓋：直接確保在 13
            if (!atV13) {
                log.info("[TR8] Transfer#{} 有帳且不需確認上蓋，MOVE → {}", transferId, VIRTUAL_13);
                return createMove(transferId, VIRTUAL_13, cid);
            }

            //log.debug("[TR8] Transfer#{} 有帳且不需確認上蓋，已在 {}，不建單", transferId, VIRTUAL_13);
            return Optional.empty();
        }

        if (site37Has) {
            if (!atV12) {
                //log.debug("[TR8] {} 有帳且 Transfer#{} {} VIRTUAL#12，MOVE → {}", SITE_37, transferId, atV12 ? "已在" : "不在", VIRTUAL_12);
                return createMove(transferId, VIRTUAL_12, null);
            }
            //log.debug("[TR8] {} 有帳但已在 {}，不建單", SITE_37, VIRTUAL_12);
            return Optional.empty();
        }

        if (site22Has) {
            if (!atV14) {
                //log.debug("[TR8] {} 有帳且 Transfer#{} {} VIRTUAL#14，MOVE → {}", SITE_22, transferId, atV14 ? "已在" : "不在", VIRTUAL_14);
                return createMove(transferId, VIRTUAL_14, null);
            }
            //log.debug("[TR8] {} 有帳但已在 {}，不建單", SITE_22, VIRTUAL_14);
            return Optional.empty();
        }

        // 其餘狀況：回待命位 VIRTUAL#12
        if (!atV12) {
            //log.debug("[TR8] 無條件匹配，Transfer#{} {} VIRTUAL#12，MOVE → {}", transferId, atV12 ? "已在" : "不在", VIRTUAL_12);
            return createMove(transferId, VIRTUAL_12, null);
        }
        //log.debug("[TR8] 無條件匹配且已在 {}，不建單", VIRTUAL_12);
        return Optional.empty();
    }

    /** 取目前 Level；若你的 DTO 欄位不同，改此處實作 */
    private Integer safeGetLevel(TransferDeviceStatus ds) {
        try {
            return ds.getLevel();
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** 建立 MOVE 請求到指定虛擬點位（containerMainId 可為 null） */
    private Optional<Long> createMove(Long transferId, String target, Long containerMainId) {
        Long targetId = locationId(target);

        TransferRequest r = new TransferRequest();
        r.setRequestKey(UUID.randomUUID().toString());
        r.setVersion(1);
        r.setRequestSource("SYSTEM");
        r.setTransferId(transferId);
        r.setTaskType("MOVE");
        r.setAccepted("N");
        r.setRequestTime(LocalDateTime.now());
        r.setCreatedTime(LocalDateTime.now());
        r.setSourceLocationName(null);
        r.setTargetLocationName(target);
        r.setSourceLocationId(null);
        r.setTargetLocationId(targetId);
        r.setContainerMainId(containerMainId);

        if (transferRequestRepository.save(r)) {
            log.info("[TR8] 建立 TransferRequest 成功: Transfer#{} → {} [MOVE], ID={}, Key={}",
                    transferId, target, r.getId(), r.getRequestKey());
            return Optional.of(r.getId());
        }
        log.warn("[TR8] 建立 TransferRequest 失敗 [MOVE → {}]", target);
        return Optional.empty();
    }

    /** 嚴格：僅回傳 container_data.cover_layers（NULL=未知→不通過） */
    private Integer getCoverLayersStrict(Long containerMainId) {
        if (containerMainId == null) return null;
        ContainerData cd = containerDataRepository.findByContainerMainId(containerMainId).orElse(null);
        return (cd == null) ? null : cd.getCoverLayers();
    }

    /** 此 container 是否需要 TR8 進 V13 前檢查上蓋（從 Site#37 來） */
    private boolean needCoverCheck(Long containerMainId) {
        if (containerMainId == null) return false;
        try {
            return containerAttrRepository.findOne(containerMainId, ATTR_NEED_COVER_CHECK)
                    .map(ContainerAttr::getAttrValue)
                    .map(String::trim)
                    .map(v -> v.equalsIgnoreCase("Y"))
                    .orElse(false);
        } catch (Exception e) {
            log.warn("[TR8] 讀取 TR8 上蓋檢查旗標失敗 cm#{}：{}", containerMainId, e.getMessage());
            return false;
        }
    }

    /** 檢查通過後可清除 flag（避免之後重複被判斷） */
    private void clearCoverCheckFlag(Long containerMainId) {
        if (containerMainId == null) return;
        try {
            containerAttrRepository.deleteOne(containerMainId, ATTR_NEED_COVER_CHECK);
            //log.debug("[TR8] 清除 TR8 上蓋檢查旗標：cm#{}", containerMainId);
        } catch (Exception e) {
            log.warn("[TR8] 清除 TR8 上蓋檢查旗標失敗 cm#{}：{}", containerMainId, e.getMessage());
        }
    }
    private boolean deviceIsRun(String deviceName) {
        return stateReader.getBestEffort(deviceName).getStatus() != ProcessStatus.STOP;
    }

    private Long locationId(String name) {
        return TransferLocationCache.requireLocationId(locationPointRepository, name);
    }
}
