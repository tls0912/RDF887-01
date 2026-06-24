package com.czkuo.rdf88701.application.generator.impl.workingbeam;

import com.czkuo.rdf88701.application.generator.WorkingBeamRequestGenerator;
import com.czkuo.rdf88701.application.service.AmrInterlockService;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.cache.WorkingBeamStatusCache;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


/**
 * WB1RequestGenerator (cache-aware for TR2)
 * - 同步帶段：Site#5、Site#6、Site#7、Site#8
 * - TR2 位置來源：TransferStatusCache（PLC 快取）
 * <p>
 * 規則：
 * 1) Site#8 有帳 → 不產單（尾端阻擋）
 * 2) TR2 有帳但不在送料位 VIRTUAL#4 → 不產單（先讓 TR2 去送料位）
 * 3) TR2 在送料位且 Site#5 為空 → 產單（即便 5~7 全空也帶入）
 * 4) 5~7 皆空且無送料位帶入需求 → 不產單
 * 5) 其餘（5/6/7 任一有帳且 8 為空）→ 產單
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component("WB1")
@RequiredArgsConstructor
public class WB1RequestGenerator implements WorkingBeamRequestGenerator {

    private final WorkingBeamRequestRepository workingBeamRequestRepository;
    private final WorkingBeamTaskRepository workingBeamTaskRepository;
    private final TransferRequestRepository transferRequestRepository;
    private final TransferTaskRepository transferTaskRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final WorkingBeamStatusCache workingBeamStatusCache;
    private final TransferStatusCache transferStatusCache; // 讀 TR2 的 PLC 快取
    private final AmrInterlockService interlock;

    private static final long TRANSFER2_ID = 2L;
    private static final String TRANSFER2_NAME = "Transfer#2";
    private static final String SITE5 = "Site#5";
    private static final String SITE6 = "Site#6";
    private static final String SITE7 = "Site#7";
    private static final String SITE8 = "Site#8";
    private static final List<String> UPSTREAM_SITES = List.of(SITE5, SITE6, SITE7);

    // Level 與虛擬點位對應：3=VIRTUAL#3(待命)、4=VIRTUAL#4(送料)
    private static final int STANDBY_LEVEL = 203;
    private static final int FEED_LEVEL = 204;

    @Override
    public Optional<Long> generateRequest(Long workingBeamId) {
        // Step 0: 任務 / 請求是否已存在
        if (workingBeamBusy(workingBeamId)) {
            //log.debug("[WB1] 已有未完成請求或任務，略過");
            return Optional.empty();
        }

        // 0) 已有未完成請求/任務 → 略過
        if (transferRequestRepository.existsUnfinishedRequestForDevice(TRANSFER2_ID)
                || transferTaskRepository.existsUnfinishedTaskForTransfer(TRANSFER2_ID)) {
            //log.debug("[WB1] Transfer#{} 已有未完成請求/任務，略過", TRANSFER2_ID);
            return Optional.empty();
        }

        String beamName = "WorkingBeam#" + workingBeamId;
        WorkingBeamDeviceStatus deviceStatus = workingBeamStatusCache.getLatest(beamName);
        if (deviceStatus == null || !deviceStatus.isValidAndComplete(3)) {
            //log.debug("[WB1] WB1 狀態快取無效（ds==null 或 !isValidAndComplete(3)），此次不建請求");
            return Optional.empty();
        }

        if (!deviceStatus.isTransferStandby()) {
            //log.debug("[WB1] WB1 設備狀態尚未準備好，此次不建請求");
            return Optional.empty();
        }

        // 站點快照
        boolean site5Has = locationTrackingRepository.hasContainerAtLocationName(SITE5);
        boolean site6Has = locationTrackingRepository.hasContainerAtLocationName(SITE6);
        boolean site7Has = locationTrackingRepository.hasContainerAtLocationName(SITE7);
        boolean site8Has = locationTrackingRepository.hasContainerAtLocationName(SITE8);

        // TR2 是否有帳（以 DB 是否掛在 Transfer#2 判斷）
        boolean tr2HasContainer = locationTrackingRepository.findContainerOnTransfer(TRANSFER2_ID).isPresent();

        // 透過快取判斷 TR2 是否在送料/待命
        TransferDeviceStatus ds = transferStatusCache.getLatest(TRANSFER2_NAME);
        boolean fresh = ds != null && ds.isValidAndComplete(3);

        if (!fresh) {
            //log.debug("[WB1] TR2 狀態快取無效（ds==null 或 !isValidAndComplete(3)），此次不建請求");
            return Optional.empty();
        }

        if (interlock.isEnable("STK04")) {
            //log.debug("[WB1] 已允許 AMR 取放，等待完成");
            return Optional.empty();
        }

        Integer level = safeGetLevel(ds);
        boolean atFeed = level != null && level == FEED_LEVEL;
        boolean atStandby = level != null && level == STANDBY_LEVEL || safeIsStandby(ds);

        // 1) 尾端阻擋
        if (site8Has) {
            //log.debug("[WB1] {} 已有帳，無法帶動，略過建立請求", SITE8);
            return Optional.empty();
        }

        // 2) TR2 有帳但不在送料位 → 不產單（等 TR2 去送料位）
        if (tr2HasContainer && !atFeed) {
            //log.debug("[WB1] TR2 有帳但不在送料位(FEED)，此次不建請求（等待 TR2 移動至 VIRTUAL#4）");
            return Optional.empty();
        }

        // 3) 在送料位且 Site#5 為空 → 需要帶入
        boolean needInfeedFromTR2 = tr2HasContainer && atFeed && !site5Has;

        // 4) 上游皆空且無帶入需求 → 不產單
        boolean anyUpstreamHas = site5Has || site6Has || site7Has;
        if (!anyUpstreamHas && !needInfeedFromTR2) {
            //log.debug("[WB1] {} / {} / {} 皆無帳，且無送料位帶入需求，略過建立請求", SITE5, SITE6, SITE7);
            return Optional.empty();
        }

        // 5) 建立同步帶動請求
        WorkingBeamRequest request = new WorkingBeamRequest();
        request.setRequestKey(UUID.randomUUID().toString());
        request.setVersion(1);
        request.setRequestSource("SYSTEM");
        request.setWorkingBeamId(workingBeamId);
        request.setDirection("IN");
        request.setAccepted("N");
        request.setRequestTime(LocalDateTime.now());
        request.setCreatedTime(LocalDateTime.now());

        boolean saved = workingBeamRequestRepository.save(request);
        if (saved) {
            log.info("[WB1] 建立 WorkingBeamRequest 成功, ID={}, Key={}, 快照: TR2(has={},feed={},standby={},fresh={},level={}), S5={}, S6={}, S7={}, S8={}",
                    request.getId(), request.getRequestKey(),
                    tr2HasContainer, atFeed, atStandby, fresh, level, site5Has, site6Has, site7Has, site8Has);
            return Optional.of(request.getId());
        } else {
            log.warn("[WB1] 建立 WorkingBeamRequest 失敗");
            return Optional.empty();
        }
    }

    /**
     * 指定工作樑裝置是否忙碌（有未完成請求或任務）
     */
    private boolean workingBeamBusy(long workingBeamId) {
        return workingBeamRequestRepository.existsUnfinishedRequestForBeam(workingBeamId)
                || workingBeamTaskRepository.existsUnfinishedTaskForBeam(workingBeamId);
    }

    /**
     * 依你的 DTO 實際欄位改這裡（例如 getCurrentLevel()/getZLevel()）
     */
    private Integer safeGetLevel(TransferDeviceStatus ds) {
        try {
            return ds.getLevel();
        } catch (Throwable ignore) {
            return null;
        }
    }

    /**
     * 若有 isTransferStandby() 就當作輔助判斷；沒有也可直接回 false
     */
    private boolean safeIsStandby(TransferDeviceStatus ds) {
        try {
            return ds.isTransferStandby();
        } catch (Throwable ignore) {
            return false;
        }
    }
}
