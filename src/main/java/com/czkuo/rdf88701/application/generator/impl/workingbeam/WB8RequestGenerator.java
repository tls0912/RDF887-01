package com.czkuo.rdf88701.application.generator.impl.workingbeam;

import com.czkuo.rdf88701.application.generator.WorkingBeamRequestGenerator;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.czkuo.rdf88701.domain.repository.ContainerDataRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.domain.repository.WorkingBeamRequestRepository;
import com.czkuo.rdf88701.domain.repository.WorkingBeamTaskRepository;
import com.czkuo.rdf88701.infra.cache.WorkingBeamStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;
import com.czkuo.rdf88701.infra.lock.InProcLocks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * WB8RequestGenerator
 * - 規則：
 *   1) Site#29 有帳 → 不建單
 *   2) 若 Site#27 有帳 → 必須確認 cover_layers 明確且 ≥ 1 才建單
 *   3) 若 Site#27 無帳 → 只要 Site#28 有帳即可建單（不檢查上蓋）
 */
@Slf4j
@Component("WB8")
@RequiredArgsConstructor
public class WB8RequestGenerator implements WorkingBeamRequestGenerator {

    private final WorkingBeamRequestRepository requestRepository;
    private final WorkingBeamTaskRepository taskRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final DeviceProcessStateReader stateReader;

    // 用於檢查 Site#27 容器的 cover_layers
    private final ContainerDataRepository containerDataRepository;

    private final WorkingBeamStatusCache workingBeamStatusCache;

    // 互斥的 Beam ID
    private static final long WB5_BEAM_ID = 5L;
    private static final long WB8_BEAM_ID = 8L;

    private static final String SITE_27 = "Site#27";
    private static final String SITE_28 = "Site#28";
    private static final String SITE_29 = "Site#29";

    @Override
    public Optional<Long> generateRequest(Long workingBeamId) {
        if (!deviceIsRun(WorkingBeamGeneratorConstants.SPLIT_MERGE_AREA))
            return Optional.empty();
        // 0) 本 Beam 是否已有未完成任務 / 請求
        if (workingBeamBusy(workingBeamId)) {
            //log.debug("[WB8] Beam#{} 已有未完成請求或任務，略過", workingBeamId);
            return Optional.empty();
        }

        // 0.1) 🔒 與 WB5 的 DB 層互斥（WB5 只要有未完成請求/任務，WB8 就不建單）
        if (workingBeamBusy(WB5_BEAM_ID)) {
            //log.debug("[WB8] 偵測到 WB5 有未完成請求/任務 → 依互斥規則不建單");
            return Optional.empty();
        }

        String beamName = "WorkingBeam#" + workingBeamId;
        WorkingBeamDeviceStatus deviceStatus = workingBeamStatusCache.getLatest(beamName);
        if (deviceStatus == null || !deviceStatus.isValidAndComplete(3)) {
            //log.debug("[WB8] WB8 狀態快取無效（ds==null 或 !isValidAndComplete(3)），此次不建請求");
            return Optional.empty();
        }

        if (!deviceStatus.isTransferStandby()) {
            //log.debug("[WB8] WB8 設備狀態尚未準備好，此次不建請求");
            return Optional.empty();
        }

        // 1) 目標站 Site#29 有帳 → 不建單
        boolean has29 = locationTrackingRepository.hasContainerAtLocationName(SITE_29);
        if (has29) {
            //log.debug("[WB8] {} 已有帳，禁止建立請求", SITE_29);
            return Optional.empty();
        }

        // 2) 來源判斷（27 / 28）
        Optional<Long> c27Opt = locationTrackingRepository.findContainerAtLocationName(SITE_27);
        Optional<Long> c28Opt = locationTrackingRepository.findContainerAtLocationName(SITE_28);
        boolean has27 = c27Opt.isPresent();
        boolean has28 = c28Opt.isPresent();

        if (!has27 && !has28) {
            //log.debug("[WB8] {} 與 {} 均無帳，略過建立請求", SITE_27, SITE_28);
            return Optional.empty();
        }

        // 2.1) 若 Site#27 有帳 → 必須「有上蓋」（cover_layers 明確且 ≥1）
        if (has27) {
            Long c27 = c27Opt.get();
            Integer cover = getCoverLayersStrict(c27); // NULL = 未知 → 不通過
            if (cover == null || cover < 1) {
                log.warn("[WB8] 阻擋：{} 容器#{} cover_layers={}（需 >=1）→ 不建單", SITE_27, c27, cover);
                return Optional.empty();
            }
            log.debug("[WB8] 檢核通過：{} 容器#{} cover_layers={}（>=1）", SITE_27, c27, cover);
        }

        // 3) 真的要建單前，再用 in-proc 做同 JVM 瞬間互斥，防同輪撞單
        final String src = has27 ? SITE_27 : SITE_28;
        return createRequest(workingBeamId, src);
    }

    /** 抽出建單並加上 in-proc 互斥（WB8 ⟂ WB5） */
    private Optional<Long> createRequest(Long workingBeamId, String sourceSiteName) {
        if (!InProcLocks.tryEnterWb8()) {
            //log.debug("[WB8] in-proc 互斥：WB5 正在動作，放棄這次建單（source={}）", sourceSiteName);
            return Optional.empty();
        }
        try {
            // 再做一次最小化的 DB 檢查，避免鎖期間狀態變化（防止邊界條件）
            if (workingBeamBusy(WB5_BEAM_ID)) {
                //log.debug("[WB8] 進入臨界區後發現 WB5 有未完成請求/任務 → 放棄建單");
                return Optional.empty();
            }

            WorkingBeamRequest request = new WorkingBeamRequest();
            request.setRequestKey(UUID.randomUUID().toString());
            request.setVersion(1);
            request.setRequestSource("SYSTEM");
            request.setWorkingBeamId(workingBeamId);
            request.setDirection("IN");
            request.setAccepted("N");
            request.setRequestTime(LocalDateTime.now());
            request.setCreatedTime(LocalDateTime.now());
            // 若 Entity 有欄位可記錄來源/目標，建議也填
            // request.setSourceLocationName(sourceSiteName);
            // request.setTargetLocationName(SITE_29);

            boolean saved = requestRepository.save(request);
            if (saved) {
                log.info("[WB8] 建立 WorkingBeamRequest 成功 (from {} → {}), ID={}, Key={}",
                        sourceSiteName, SITE_29, request.getId(), request.getRequestKey());
                return Optional.of(request.getId());
            } else {
                log.warn("[WB8] 建立 WorkingBeamRequest 失敗 (from {} → {})", sourceSiteName, SITE_29);
                return Optional.empty();
            }
        } finally {
            InProcLocks.exitWb8();
        }
    }

    /** 嚴格：僅回傳 container_data.cover_layers（NULL=未知→不通過） */
    private boolean workingBeamBusy(long workingBeamId) {
        return requestRepository.existsUnfinishedRequestForBeam(workingBeamId)
                || taskRepository.existsUnfinishedTaskForBeam(workingBeamId);
    }

    private Integer getCoverLayersStrict(Long containerMainId) {
        if (containerMainId == null) return null;
        ContainerData cd = containerDataRepository.findByContainerMainId(containerMainId).orElse(null);
        return (cd == null) ? null : cd.getCoverLayers(); // NULL = 未知 → 不符合條件
    }
    private boolean deviceIsRun(String deviceName) {
        return stateReader.getBestEffort(deviceName).getStatus() != ProcessStatus.STOP;
    }
}
