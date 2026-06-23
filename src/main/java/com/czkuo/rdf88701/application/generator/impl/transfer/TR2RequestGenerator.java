package com.czkuo.rdf88701.application.generator.impl.transfer;

import com.czkuo.rdf88701.application.generator.TransferRequestGenerator;
import com.czkuo.rdf88701.application.service.command.ContainerCreateService;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.entity.TransferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.czkuo.rdf88701.application.monitor.AutoR029Planner.TestMode;

/**
 * TR2RequestGenerator
 * - 位置來源：TransferStatusCache（PLC 快取）
 * - VIRTUAL#3 = 待命位（接料）
 * - VIRTUAL#4 = 送料位（給 WB1 取料進 Site#5）
 * <p>
 * 規則：
 * 1) 有帳：不在送料位 -> MOVE → VIRTUAL#4；在送料位 -> 不動
 * 2) 無帳：不在待命位 -> MOVE → VIRTUAL#3；在待命位 -> 不動
 * 3) 狀態未知/不完整：有帳→往送料位；無帳→回待命位
 */

/**
 * TR2RequestGenerator
 * - 位置來源：TransferStatusCache（PLC 快取）
 * - VIRTUAL#3 = 待命位（接料）
 * - VIRTUAL#4 = 送料位（給 WB1 取料進 Site#5）
 *
 * 規則：
 * 1) 有帳：不在送料位 -> MOVE → VIRTUAL#4；在送料位 -> 不動
 * 2) 無帳：不在待命位 -> MOVE → VIRTUAL#3；在待命位 -> 不動
 * 3) 狀態未知/不完整：此次不建請求（保持保守）
 *
 * 自動建帳（無帳但 PLC 回報有載具時）：
 * - 條碼：TY + 4位數字 + 2位大寫英文字母（例：TY1234AB）
 * - container_type=TRAY、container_code=barcode、alias_code=barcode
 * - content_kind=NORMAL_WITH_COVER、tray_thickness_mm=5.62(mm)
 * - 建檔後立即 entry 到點位 251L
 */
@Slf4j
@Component("TR2")
@RequiredArgsConstructor
public class TR2RequestGenerator implements TransferRequestGenerator {

    private final ContainerCreateService containerCreateService;
    private final WorkingBeamRequestRepository workingBeamRequestRepository;
    private final WorkingBeamTaskRepository workingBeamTaskRepository;
    private final TransferRequestRepository transferRequestRepository;
    private final TransferTaskRepository transferTaskRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final TransferStatusCache statusCache; // 以快取取得 PLC 狀態

    private static final long   WORKINGBEAM1_ID = 1L;
    private static final String STANDBY_CODE    = "VIRTUAL#3";
    private static final String FEED_CODE       = "VIRTUAL#4";

    // 依 PLC 對應邏輯設定：Level 3=待命、4=送料
    private static final int STANDBY_LEVEL = 203;
    private static final int FEED_LEVEL    = 204;

    // TR2 自動建帳的點位（沿用你原本的 251L）
    private static final Long TR2_AUTO_ENTRY_LOCATION_ID = 251L;

    @Override
    public Optional<Long> generateRequest(Long transferId) {
        // 0) 若任一方已有未完成請求/任務 → 略過
        if (transferRequestRepository.existsUnfinishedRequestForDevice(transferId)
                || transferTaskRepository.existsUnfinishedTaskForTransfer(transferId)) {
            //log.debug("[TR2] Transfer#{} 已有未完成請求/任務，略過", transferId);
            return Optional.empty();
        }
        if (workingBeamRequestRepository.existsUnfinishedRequestForBeam(WORKINGBEAM1_ID)
                || workingBeamTaskRepository.existsUnfinishedTaskForBeam(WORKINGBEAM1_ID)) {
            //log.debug("[WB1] 已有未完成請求或任務，略過");
            return Optional.empty();
        }

        // 1) 判斷是否載貨（容器是否在 Transfer 上）
        Optional<Long> containerOnTransfer = locationTrackingRepository.findContainerOnTransfer(transferId);
        boolean hasContainer = containerOnTransfer.isPresent();

        // 2) 從 cache 取設備狀態
        String transferName = "Transfer#" + transferId;
        TransferDeviceStatus ds = statusCache.getLatest(transferName);
        boolean fresh = ds != null && ds.isValidAndComplete(3);
        if (!fresh) {
            //log.debug("[TR2] TR2 狀態快取無效，略過此次請求生成");
            return Optional.empty();
        }

        Integer level = safeGetLevel(ds);
        boolean atStandby = level != null && level == STANDBY_LEVEL;
        boolean atFeed    = level != null && level == FEED_LEVEL;

        // 2.5) 若 PLC 指示「有載具」但系統「無帳」→ 依規則自動建檔＋建帳（L005風格）
        if (TestMode && !hasContainer && ds.isProductPresent()) {
            String barcode = genTyBarcode(); // 例：TY1234AB
            Long newContainerId = containerCreateService.createAndEntryRealTrayForLocationAuto(
                    barcode,
                    TR2_AUTO_ENTRY_LOCATION_ID,
                    "NORMAL_WITH_COVER"
                    //"ALL_COVER"
            );
            log.info("[TR2] 自動建檔完成 containerId={} barcode={}", newContainerId, barcode);

            // 重新判斷是否已在 Transfer 上（建帳後可能已在該 Transfer 的點位）
            containerOnTransfer = locationTrackingRepository.findContainerOnTransfer(transferId);
            hasContainer = containerOnTransfer.isPresent();
        }

        // 3) 決策（有帳→送料位；無帳→待命位）
        if (hasContainer && ds.isProductPresent()) {
            if (!atFeed) {
                //log.debug("[TR2] 有帳且不在送料位，MOVE → {}", FEED_CODE);
                return createMove(transferId, FEED_CODE, containerOnTransfer.get());
            }
            //log.debug("[TR2] 有帳且已在送料位 {}，不建單", FEED_CODE);
            return Optional.empty();
        } else {
            if (!atStandby) {
                //log.debug("[TR2] 無帳且不在待命位，MOVE → {}", STANDBY_CODE);
                return createMove(transferId, STANDBY_CODE, null);
            }
            //log.debug("[TR2] 無帳且已在待命位 {}，不建單", STANDBY_CODE);
            return Optional.empty();
        }
    }

    /** 嘗試取得目前 Level；若你的 DTO 名稱不同，改這裡 */
    private Integer safeGetLevel(TransferDeviceStatus ds) {
        try {
            return ds.getLevel();
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** 建一筆 MOVE 請求到指定虛擬點位 */
    private Optional<Long> createMove(Long transferId, String target, Long containerMainId) {
        Long targetId = target != null ? locationId(target) : null;

        TransferRequest r = new TransferRequest();
        r.setRequestKey(UUID.randomUUID().toString());
        r.setVersion(1);
        r.setRequestSource("SYSTEM");
        r.setTransferId(transferId);
        r.setTaskType("MOVE"); // 你們系統若用 GOTO，改這裡
        r.setAccepted("N");
        r.setRequestTime(LocalDateTime.now());
        r.setCreatedTime(LocalDateTime.now());
        r.setSourceLocationName(null);
        r.setTargetLocationName(target);
        r.setSourceLocationId(null);
        r.setTargetLocationId(targetId);
        r.setContainerMainId(containerMainId);

        if (transferRequestRepository.save(r)) {
            log.info("[TR2] 建立 TransferRequest 成功: → {} [MOVE], ID={}, Key={}", target, r.getId(), r.getRequestKey());
            return Optional.of(r.getId());
        }
        log.warn("[TR2] 建立 TransferRequest 失敗 [MOVE → {}]", target);
        return Optional.empty();
    }

    // ---------- 條碼產生器：TY + 4位數字 + 2位大寫英文 ----------
    private static final SecureRandom RAND = new SecureRandom();
    private String genTyBarcode() {
        int num = RAND.nextInt(10_000);             // 0000..9999
        String digits = String.format("%04d", num);
        char c1 = (char) ('A' + RAND.nextInt(26));
        char c2 = (char) ('A' + RAND.nextInt(26));
        return "TY" + digits + c1 + c2;
    }

    private Long locationId(String name) {
        return TransferLocationCache.requireLocationId(locationPointRepository, name);
    }
}
