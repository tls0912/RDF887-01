package com.czkuo.rdf88701.application.generator.impl.workingbeam;

import com.czkuo.rdf88701.application.generator.WorkingBeamRequestGenerator;
import com.czkuo.rdf88701.application.service.AmrInterlockService;
import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.command.ContainerCreateService;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.plc.state.site.SiteDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.SiteStatusCache;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.cache.WorkingBeamStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.LocationTracking;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

import static com.czkuo.rdf88701.application.monitor.AutoR029Planner.TestMode;

/**
 * WB2RequestGenerator
 * - 先交握 Site#17 → 再依 Site#17~20 是否有帳判斷是否建立請求
 * - 規則加強：
 * 若來源是 Site#20，必須同時滿足：
 * (a) Transfer#7 位置在 VIRTUAL#10（以 Level 對應）
 * (b) Transfer#7 無帳（DB）
 * 否則禁止建立 WB 請求。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component("WB2")
@RequiredArgsConstructor
public class WB2RequestGenerator implements WorkingBeamRequestGenerator {

    private final ContainerCreateService containerCreateService;
    private final WorkingBeamRequestRepository requestRepository;
    private final WorkingBeamTaskRepository taskRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerMainRepository containerMainRepository;
    private final ContainerAttrRepository containerAttrRepository;
    private final PlcAccessService plcAccessService;
    private final WorkingBeamStatusCache workingBeamStatusCache;
    private final SiteStatusCache siteStatusCache;           // Site PLC 狀態快取
    private final TransferStatusCache transferStatusCache;   // Transfer PLC 狀態快取（用來檢查 TR7）
    private final AmrInterlockService interlock;
    private final DeviceProcessStateReader stateReader;

    private static final List<String> LINEAR_SITES = List.of("Site#20", "Site#19", "Site#18", "Site#17");

    private static final long SITE17_POINT_ID = 215L;
    private static final Long SITE17_ID = 215L;

    // TR7 常數
    private static final long TRANSFER7_ID = 7L;
    private static final String TRANSFER7_NAME = "Transfer#7";

    // TR7 在 VIRTUAL#10 時的 Level 值
    private static final int VIRTUAL10_LEVEL = 210;

    @Override
    public Optional<Long> generateRequest(Long workingBeamId) {
        if (!deviceIsRun(WorkingBeamGeneratorConstants.SPLIT_MERGE_AREA))
            return Optional.empty();
        // Step 0: 任務 / 請求是否已存在
        if (workingBeamBusy(workingBeamId)) {
            //log.debug("[WB2] 已有未完成請求或任務，略過");
            return Optional.empty();
        }

        // Step 1: 對 Site#17（若 PLC 顯示有料）做帳號寫入與 Handshake
        try {
            tryHandshakeWithSite17();
        } catch (Exception e) {
            log.warn("[WB2] 與 Site#17 交握例外，略過此次請求生成：{}", e.getMessage());
            return Optional.empty();
        }

        String beamName = "WorkingBeam#" + workingBeamId;
        WorkingBeamDeviceStatus deviceStatus = workingBeamStatusCache.getLatest(beamName);
        //if (deviceStatus == null || !deviceStatus.isValidAndComplete(3)) {
        if (deviceStatus == null) {
            //log.debug("[WB2] WB2 狀態快取無效（ds==null 或 !isValidAndComplete(3)），此次不建請求");
            return Optional.empty();
        }

        if (!deviceStatus.isTransferStandby()) {
            //log.debug("[WB2] WB2 設備狀態尚未準備好，此次不建請求");
            return Optional.empty();
        }

        if (interlock.isEnable("STK05")) {
            //log.debug("[WB2] 已允許 AMR 取放，等待完成");
            return Optional.empty();
        }

        // Step 2: 找出第一個「DB 有帳」的來源 site（優先 20 → 19 → 18 → 17）
        String matchedSite = null;
        boolean site20HasAccount = false;

        for (String siteName : LINEAR_SITES) {
            Optional<Long> containerAtSite = locationTrackingRepository.findContainerAtLocationName(siteName);
            if (containerAtSite.isEmpty()) continue;

            matchedSite = siteName;
            site20HasAccount = "Site#20".equals(siteName);
            break;
        }

        if (matchedSite == null) {
            //log.debug("[WB2] Site#17~20 無可用容器，略過建立請求");
            return Optional.empty();
        }

        // Step 3: 若來源為 Site#20，要求 TR7 在 VIRTUAL#10 且 TR7 無帳
        if (site20HasAccount) {
            boolean tr7HasContainer = locationTrackingRepository.findContainerOnTransfer(TRANSFER7_ID).isPresent();

            TransferDeviceStatus ds7 = transferStatusCache.getLatest(TRANSFER7_NAME);
            boolean ds7Fresh = ds7 != null && ds7.isValidAndComplete(3);
            boolean tr7AtV10 = ds7Fresh && isAtVirtual10ByLevel(ds7);

            if (!(tr7AtV10 && !tr7HasContainer)) {
                //log.debug("[WB2] 來源=Site#20，但條件不符：TR7 必須在 VIRTUAL#10 且 無帳。實際：atV10={}, hasContainer={}, ds7Fresh={}",
//                        tr7AtV10, tr7HasContainer, ds7Fresh);
                return Optional.empty();
            }
        }

        // Step 4: 建立 WB 請求
        WorkingBeamRequest request = new WorkingBeamRequest();
        request.setRequestKey(UUID.randomUUID().toString());
        request.setVersion(1);
        request.setRequestSource("SYSTEM");
        request.setWorkingBeamId(workingBeamId);
        request.setDirection("IN");
        request.setAccepted("N");
        request.setRequestTime(LocalDateTime.now());
        request.setCreatedTime(LocalDateTime.now());

        boolean saved = requestRepository.save(request);
        if (saved) {
            log.info("[WB2] 建立 WorkingBeamRequest 成功, ID={}, Key={}, 來源={}", request.getId(), request.getRequestKey(), matchedSite);
            return Optional.of(request.getId());
        } else {
            log.warn("[WB2] 建立 WorkingBeamRequest 失敗");
            return Optional.empty();
        }
    }

    /**
     * 若 Site#17 有帳（PLC productPresent=true）→ 寫入 aliasCode 並以 B28B/B88B 完成交握
     */
    private void tryHandshakeWithSite17() {
        final String siteName = "Site#17";

        SiteDeviceStatus status = siteStatusCache.getLatest(siteName);
        if (status == null || !status.isProductPresent()) return;

        Optional<LocationTracking> trackingOpt = locationTrackingRepository.findByLocationPointId(SITE17_POINT_ID);
        if (TestMode && trackingOpt.isEmpty()) {
            // PLC 有料但 DB 無帳 → 自動建檔 + Entry
            String barcode = genTyBarcode(); // 例：TY1234AB
            Long newContainerId = containerCreateService.createAndEntryRealTrayForLocationAuto(barcode, SITE17_ID, "UNKNOWN");
            log.info("[WB2] Site#17 自動建檔完成 containerId={} barcode={}", newContainerId, barcode);
            trackingOpt = locationTrackingRepository.findByLocationPointId(SITE17_POINT_ID);
            if (trackingOpt.isEmpty()) return;
            return;
        }

        Long containerMainId = trackingOpt.get().getContainerMainId();
        Optional<ContainerMain> containerOpt = containerMainRepository.findById(containerMainId);
        if (containerOpt.isEmpty()) return;

        upsertAttr(containerOpt.get().getId(), "bin_type", "B", "type");

        String aliasCode = containerOpt.get().getAliasCode();

        // Handshake：B28B(REQ) → W5E6(Next) → 等 B88B(ACK)
        plcAccessService.writeBoolean("PLC-Packer", "B28B", false);

        String current = plcAccessService.readString("PLC-Packer", "W15E4", 25);
        String next = plcAccessService.readString("PLC-Packer", "W1604", 25);

        if (current != null && !current.trim().isEmpty()) {
            //log.debug("[WB2] Site#17 PLC 已有帳 '{}', 略過寫入", current);
        } else {
            if (next == null || !next.equals(aliasCode)) {
                plcAccessService.writeString("PLC-Packer", "W5E6", aliasCode);
                log.info("[WB2] Site#17 寫入 aliasCode={} 至 PLC(W5E6)", aliasCode);
            }
            plcAccessService.writeBoolean("PLC-Packer", "B28B", true);

            // 等待 B88B 回應（最長 3 秒）
            for (int i = 0; i < 15; i++) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                boolean ack = plcAccessService.readBoolean("PLC-Packer", "B88B");
                if (ack) {
                    plcAccessService.writeBoolean("PLC-Packer", "B28B", false);
                    log.info("[WB2] PLC 回應 B88B 成功，完成 Site#17 帳資料 handshake");
                    return;
                }
            }
            log.warn("[WB2] Site#17 寫入 PLC 後超時未收到 B88B Ack，保留 B28B=True");
        }
    }

    /**
     * 指定工作樑裝置是否忙碌（有未完成請求或任務）
     */
    private boolean workingBeamBusy(long workingBeamId) {
        return requestRepository.existsUnfinishedRequestForBeam(workingBeamId)
                || taskRepository.existsUnfinishedTaskForBeam(workingBeamId);
    }

    // ---------- 屬性寫入/更新 ----------
    private void upsertAttr(Long containerId, String key, String value, String unit) {
        ContainerAttr a = new ContainerAttr();
        a.setContainerMainId(containerId);
        a.setAttrKey(key);
        a.setAttrValue(value);
        a.setUnit(unit);
        containerAttrRepository.upsert(a); // 需 UNIQUE KEY (container_main_id, attr_key)
    }

    // ---------- TR7 位置判斷（僅用 Level） ----------
    private boolean isAtVirtual10ByLevel(TransferDeviceStatus ds) {
        Integer level = safeGetLevel(ds);
        return level != null && level == VIRTUAL10_LEVEL;
    }

    private Integer safeGetLevel(TransferDeviceStatus ds) {
        try {
            return ds.getLevel();
        } catch (Throwable ignore) {
            return null;
        }
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
    private boolean deviceIsRun(String deviceName) {
        return stateReader.getBestEffort(deviceName).getStatus() != ProcessStatus.STOP;
    }
}
