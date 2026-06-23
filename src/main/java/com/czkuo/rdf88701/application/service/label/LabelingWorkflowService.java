package com.czkuo.rdf88701.application.service.label;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.ZebraPrintService;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.r029.R029OutputCaptureService;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.domain.plc.state.site.SiteDeviceStatus;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.infra.cache.SiteStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.LabelingInfo;
import com.czkuo.rdf88701.infra.event.model.labeling.LabelingInfoReadyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * LabelingWorkflowService
 * ------------------------------------------------------------
 * - 以 PLC 現場狀態為主（SiteStatusCache），DB 僅為輔助
 * - Report 握手（B081A=1 且我方 B021A=0）
 * - 由 PLC 的 level (W13B1) 判斷上報站點 → 檢查站點快取有效 → 設定 watermark
 * - (可選)送 S020 → 取單 → ZPL → 列印 → Ack
 * - 命令/完成 三段握手補償
 * - 設備 Wait CMD 時送命
 * <p>
 * Monitor 現在只剩排程呼叫 runTick()。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabelingWorkflowService {

    /* ========= 依賴 ========= */
    private final PlcAccessService plc;
    private final ContainerMainRepository containerMainRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final LabelingInfoService labelingInfoService;
    private final ZebraPrintService zebra;
    private final ZplTemplateService zplTemplateService;
    private final MqttCommandService mqttCommandService; // 若要送 S020 使用
    private final SiteStatusCache siteStatusCache;
    private final R029OutputCaptureService r029OutputCaptureService; // 貼標成功要進位 R029 狀態

    /* ========= 設定 ========= */
    @Value("${labeler.device-name:PLC-Packer}")
    private String device;

    @Value("${labeler.printer.ip}")
    private String printerIp;
    @Value("${labeler.printer.port:9100}")
    private int printerPort;

    @Value("${labeler.zpl.font-size:60}")
    private int zplFontSize;

    @Value("${labeler.s020.enabled:false}")
    private boolean enableS020;
    @Value("${labeler.s020.target-system:ase}")
    private String s020TargetSystem;

    /* ========= 時間參數 ========= */
    private static final Duration CONNECT_TMO = Duration.ofSeconds(10);
    private static final Duration READ_TMO = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);
    private static final Duration OVERALL_TMO = Duration.ofSeconds(30);
    private static final Duration REPORT_WAIT_TMO = Duration.ofSeconds(20);

    /* ========= PLC 位址 ========= */
    // Write Bit (我方 → 貼標機)
    private static final String B_READY = "B0218";
    private static final String B_REPORT_ACK = "B021A";
    private static final String B_CMD_REQ = "B021B";
    private static final String B_COMP_ACK = "B021C";
    // Read Bit (貼標機 → 我方)
    private static final String B_STANDBY = "B0818";
    private static final String B_REPORT_REQ = "B081A";
    private static final String B_CMD_ACK = "B081B";
    private static final String B_COMP_REQ = "B081C";
    // Write Word (我方 → 貼標機)
    private static final String W_NO = "W03B0";
    private static final String W_COUNT = "W03B2";
    private static final String W_MODE = "W03B3";
    // Read Word (貼標機 → 我方)
    private static final String W_LEVEL = "W13B1"; // 從 PLC 讀 level 來決定站點
    private static final String W_STATUS = "W13B3";
    private static final String W_RETCODE = "W13B6";

    /* ========= 站點/策略 ========= */
    private static final String SITE_30 = "Site#30";
    private static final String SITE_41 = "Site#41";
    private static final int MAIN_LEVEL = 30;  // level=30 → Site#30
    private static final int SUB_LEVEL = 41;  // level=41 → Site#41
    private static final int DEFAULT_LABEL_COUNT = 1;
    private static final int MODE_NEED_LABELING = 1;

    /* ========= 會話狀態（避免重覆送 S020 / 控制等待） ========= */
    private volatile boolean reportSessionActive = false;
    private volatile String reportSessionSite = null;
    private volatile Long reportSessionContainerId = null;
    private volatile long reportSessionStartMs = 0L;

    private int lastSentLabelNo = -1;

    /* ================== 對外入口（排程呼叫） ================== */
    public void runTick() {
        handleReportHandshake();
        compensateCompletionHandshake();
        requestCommandIfDeviceReady();
    }

    /* ================== Report 握手 ================== */
    private void handleReportHandshake() {
        boolean reportReq = plc.readBoolean(device, B_REPORT_REQ);
        boolean reportAck = plc.readBoolean(device, B_REPORT_ACK);

        if (reportReq && !reportAck) {
            // 第一次進入會話：由 PLC level 決定站點 → 先看現場快取 → 設 watermark → (可選)送 S020
            int level = plc.readInt32(device, W_LEVEL);
            String site = mapLevelToSite(level);
            SiteDeviceStatus ds = siteStatusCache.getLatest(site);
            if (!reportSessionActive) {
                if (site == null) {
                    //log.debug("[Labeling] ReportReq=1，但 PLC level={} 無對應站點，暫不回 Ack", level);
                    return;
                }
                // 先用 SiteStatusCache 驗證現場是否可列印（抓帳主要抓 PLC）
                if (!isSiteCacheValidForReport(ds)) {
                    //log.debug("[Labeling] Site 快取狀態不完整/非閒置/無產品，暫不回 Ack. site={}, ds={}", site, ds);
                    return;
                }

                // 以 PLC productId → ContainerMain（序號→alias_code；再退 lot_no）
                Long containerId = resolveContainerIdFromPlc(site, ds);

                // 仍找不到再 fallback：用 DB 的 location_tracking 依站點查
                if (containerId == null) {
                    containerId = locationTrackingRepository.findContainerAtLocationName(site).orElse(null);
                    if (containerId != null) {
                        log.info("[Labeling] Fallback container from DB tracking: site={}, containerId={}", site, containerId);
                    } else {
                        log.info("[Labeling] 無法從 PLC/DB 取得 containerId，site={} ds={}，後續列印流程以 site 為主繼續（允許 null）", site, ds);
                        return;
                    }
                }

                labelingInfoService.markWatermarkForSite(site);
                if (enableS020) {
                    publishS020Event2003(site, containerId);
                }

                reportSessionActive = true;
                reportSessionSite = site;
                reportSessionContainerId = containerId;
                reportSessionStartMs = System.currentTimeMillis();
                log.info("[Labeling] 📍 Report session started: site={}, containerId={}, level={}, ds={}",
                        site, containerId, level, ds == null ? "null" : ds.toSimpleString());
            }

            // 嘗試領取 watermark 後第一筆 READY → ZPL → 列印 → Ack
            var claimedOpt = labelingInfoService.claimFirstReadyAfter(
                    reportSessionSite, reportSessionContainerId, /*preferredLabelNo*/ 1);

            if (claimedOpt.isEmpty()) {
                long waitMs = System.currentTimeMillis() - reportSessionStartMs;
                if (waitMs > REPORT_WAIT_TMO.toMillis()) {
                    //log.warn("[Labeling] ⏱️ 等待 S065/S066 超時（{} ms），仍未回 Ack；可考慮補償重送 S020", waitMs);
                    log.warn("[Labeling] ⏱️ 等待 S065/S066 超時（{} ms），仍未回 Ack；重送 S020", waitMs);
                    if (site == null) {
                        //log.debug("[Labeling] site==null，無法重送 S020");
                        return;
                    }
                    // 先用 SiteStatusCache 驗證現場是否可列印（抓帳主要抓 PLC）
                    if (!isSiteCacheValidForReport(ds)) {
                        //log.debug("[Labeling] Site 快取狀態不完整/非閒置/無產品，無法重送 S020");
                        return;
                    }
                    Long containerId = resolveContainerIdFromPlc(site, ds);
                    publishS020Event2003(site, containerId);
                }
                return;
            }

            // 列印前再次確認現場仍有效（避免等待期間現場變動）
            SiteDeviceStatus now = siteStatusCache.getLatest(reportSessionSite);
            if (!isSiteCacheValidForReport(now)) {
                log.warn("[Labeling] Site 快取狀態失效或非閒置/無產品，取消列印與 Ack. site={}", reportSessionSite);
                return;
            }

            LabelingInfo info = claimedOpt.get();
            var vars = labelingInfoService.extractLabelVars(info);
            String zpl = "S066".equalsIgnoreCase(vars.getType())
                    ? zplTemplateService.buildDetailedS066(vars, zplFontSize)
                    : zplTemplateService.buildBasicSevenFields(vars, zplFontSize);


            var result = zebra.printAndWait(
                    printerIp, printerPort, zpl,
                    CONNECT_TMO, READ_TMO, POLL_INTERVAL, OVERALL_TMO
            );

            if (result.ok()) {
                plc.writeBoolean(device, B_REPORT_ACK, true);
                labelingInfoService.markUsed(info.getId());
                labelingInfoService.clearWatermarkForSite(reportSessionSite);

                // 同步 R029：將該載具由 STRAPPED 進位到 LABELED（若先前沒有，補一筆 LABELED）
                try {
                    if (reportSessionContainerId != null) {
                        r029OutputCaptureService.markLabeledIfBelongs(reportSessionContainerId);
                        log.info("[Labeling] markLabeledIfBelongs success, site={}, containerId={}, tid={}, key={}",
                                reportSessionSite, reportSessionContainerId, info.getTid(), info.getRequestKey());
                    } else {
                        log.warn("[Labeling] markLabeledIfBelongs skipped: reportSessionContainerId is null");
                    }
                } catch (Exception ex) {
                    log.error("[Labeling] markLabeledIfBelongs() failed", ex);
                }

                log.info("[Labeling] 🖨️ Label printed → ReportAck=1, site={}, containerId={}, tid={}, key={}",
                        reportSessionSite, reportSessionContainerId, info.getTid(), info.getRequestKey());
                resetReportSession();
            } else {
                log.warn("[Labeling] 🖨️ Print failed, NOT ack. msg={}", result.message());
            }

            plc.writeBoolean(device, B_REPORT_ACK, true);
            labelingInfoService.markUsed(info.getId());
            labelingInfoService.clearWatermarkForSite(reportSessionSite);
            log.info("[Labeling] 🖨️ Label printed → ReportAck=1, site={}, containerId={}, tid={}, key={}",
                    reportSessionSite, reportSessionContainerId, info.getTid(), info.getRequestKey());
            resetReportSession();

            return;
        }

        // PLC 放掉 ReportReq → 回收 Ack 並清會話
        if (!reportReq && reportAck) {
            plc.writeBoolean(device, B_REPORT_ACK, false);
            //log.debug("[Labeling] ReportAck reset (B021A=0)");
        }
        if (!reportReq && reportSessionActive) {
            //log.debug("[Labeling] Report session ended by PLC → cleanup.");
            resetReportSession();
        }
    }

    /* ================== 事件喚醒（可選，用於 S065/S066 到庫即嘗試列印） ================== */
    @EventListener
    public void onLabelingInfoReady(LabelingInfoReadyEvent ev) {
        try {
            if (!reportSessionActive) return; // 僅在 Report 握手期間有意義
            if (ev.getSiteCode() != null && reportSessionSite != null && !reportSessionSite.equals(ev.getSiteCode())) {
                return;
            }

            // 列印前以快取再次確認現場狀態
            SiteDeviceStatus now = siteStatusCache.getLatest(reportSessionSite);
            if (!isSiteCacheValidForReport(now)) {
                //log.debug("[Labeling] (event) Site 快取狀態不適合列印，略過. site={}", reportSessionSite);
                return;
            }

            var claimedOpt = labelingInfoService.claimFirstReadyAfter(
                    reportSessionSite, reportSessionContainerId, 1);
            if (claimedOpt.isEmpty()) return;

            LabelingInfo info = claimedOpt.get();
            var vars = labelingInfoService.extractLabelVars(info);
            String zpl = "S066".equalsIgnoreCase(vars.getType())
                    ? zplTemplateService.buildDetailedS066(vars, zplFontSize)
                    : zplTemplateService.buildBasicSevenFields(vars, zplFontSize);

            var result = zebra.printAndWait(
                    printerIp, printerPort, zpl,
                    CONNECT_TMO, READ_TMO, POLL_INTERVAL, OVERALL_TMO
            );

            if (result.ok()) {
                plc.writeBoolean(device, B_REPORT_ACK, true);
                labelingInfoService.markUsed(info.getId());
                labelingInfoService.clearWatermarkForSite(reportSessionSite);
                log.info("[Labeling] (event) printed & acked: site={}, key={}",
                        reportSessionSite, info.getRequestKey());
                resetReportSession();
            }
        } catch (Exception e) {
            log.warn("[Labeling] onLabelingInfoReady error", e);
        }
    }

    /* ================== 命令/完成 三段補償 ================== */
    private void compensateCompletionHandshake() {
        boolean cmdAck = plc.readBoolean(device, B_CMD_ACK);
        boolean cmdReq = plc.readBoolean(device, B_CMD_REQ);
        boolean compReq = plc.readBoolean(device, B_COMP_REQ);
        boolean compAck = plc.readBoolean(device, B_COMP_ACK);

        if (cmdAck && cmdReq) {
            plc.writeBoolean(device, B_CMD_REQ, false);
            //log.debug("[Labeling] Recalled CMD_REQ (PLC holds CMD_ACK)");
            return;
        }

        if (compReq && !compAck) {
            int ret = plc.readInt32(device, W_RETCODE);
            switch (ret) {
                case 0x0100 -> log.info("[Labeling] ✅ Command Success (0x0100), lastLabelNo={}", lastSentLabelNo);
                case 0x0800 -> log.warn("[Labeling] ⚠️ Command Abort (0x0800)");
                case 0x0F00 -> log.error("[Labeling] ❌ Command Fail (0x0F00)");
                default -> log.info("[Labeling] ℹ️ Waiting valid RetCode... ret=0x{}", Integer.toHexString(ret));
            }
            plc.writeBoolean(device, B_COMP_ACK, true);
            //log.debug("[Labeling] Sent COMP_ACK");
            return;
        }

        if (!compReq && compAck) {
            plc.writeBoolean(device, B_COMP_ACK, false);
            //log.debug("[Labeling] Reset COMP_ACK");
        }
    }

    /* ================== Wait CMD → 送命 ================== */
    private void requestCommandIfDeviceReady() {
        if (!plc.readBoolean(device, B_STANDBY)) return;

        int status = plc.readInt32(device, W_STATUS);
        int deviceStatus = status & 0xF;        // 1 Idle, 2 Report, 3 Wait CMD, 4 Processing, 5 Complete
        int runningStatus = (status >> 8) & 0xF; // 1 IDLE, 2 Labeling
        if (!(deviceStatus == 3 && runningStatus == 1)) return;

        int labelNo = 1; // 你的策略：可由 DB/配置決定
        lastSentLabelNo = labelNo;

        log.info("[Labeling] ▶️ Send CMD_REQ, LABEL_NO={}, COUNT={}, MODE={}",
                labelNo, DEFAULT_LABEL_COUNT, MODE_NEED_LABELING);

        plc.writeBoolean(device, B_READY, true);
        plc.writeInt32(device, W_NO, labelNo);
        plc.writeInt32(device, W_COUNT, DEFAULT_LABEL_COUNT);
        plc.writeInt32(device, W_MODE, MODE_NEED_LABELING);
        plc.writeBoolean(device, B_CMD_REQ, true);
    }

    /* ================== 輔助 ================== */

    /**
     * 將 PLC 的 level 數值對應到站點字串，未知回 null。
     */
    private String mapLevelToSite(int level) {
        if (level == MAIN_LEVEL) return SITE_30;
        if (level == SUB_LEVEL) return SITE_41;
        return null;
    }

    /**
     * 以 SiteStatusCache 驗證該站點是否可進入列印流程（抓帳主要抓 PLC）。
     */
    private boolean isSiteCacheValidForReport(SiteDeviceStatus ds) {
        if (ds == null) return false;
        if (!ds.isValidAndComplete(3)) return false; // 3 秒可調
        if (!ds.isIdle()) return false;
        if (!ds.isProductPresent()) return false;    // 若允許空站列印可放寬
        return true;
    }

    /**
     * 先用 PLC 現場帳解析 containerMainId：
     * productId → ContainerMain.alias_code → ContainerMain.lot_no
     * 任一命中即回傳 id，否則回 null.
     */
    private Long resolveContainerIdFromPlc(String site, SiteDeviceStatus ds) {
        try {
            String raw = ds.getProductId();
            String productId = raw == null ? "" : raw.trim();
            if (productId.isEmpty()) {
                //log.debug("[Labeling] PLC productId is empty. site={}", site);
                return null;
            }
            // 1) 以序號對應
            try {
                Optional<ContainerMain> byAlias = containerMainRepository.findByAliasCode(productId);
                if (byAlias.isPresent()) {
                    return byAlias.get().getId();
                }
            } catch (Throwable ignore) {
                // 若專案尚未實作 findByAliasCode，不中斷流程，直接嘗試 lotNo
            }
            // 2) 以 LOT 對應
            // try {
            //     Optional<ContainerMain> byLot = containerMainRepository.findByLotNo(productId);
            //     if (byLot.isPresent()) {
            //         return byLot.get().getId();
            //     }
            // } catch (Throwable ignore) {
            //     // 同上：未實作則略過
            // }
            //log.debug("[Labeling] PLC productId='{}' not found in ContainerMain. site={}", productId, site);
            return null;
        } catch (Exception e) {
            log.warn("[Labeling] resolveContainerIdFromPlc error. site={}", site, e);
            return null;
        }
    }

    /**
     * 發 S020(2003)：「拆/併打帶完成，等待標籤資訊」
     * - 從 ContainerMain 取 LotNo / AliasCode
     */
    private void publishS020Event2003(String siteCode, Long containerMainId) {
        try {
            if (containerMainId == null) {
                log.warn("[Labeling] S020(2003) skipped: containerMainId is null (site={})", siteCode);
                return;
            }

            Optional<ContainerMain> cmOpt = containerMainRepository.findById(containerMainId);
            if (cmOpt.isEmpty()) {
                log.warn("[Labeling] S020(2003) skipped: containerMain not found, id={}, site={}", containerMainId, siteCode);
                return;
            }

            ContainerMain cm = cmOpt.get();

            // === 依你的資料模型對應 ===
            String lotId = nz(cm.getLotNo());
            String carrierId = nz(cm.getAliasCode());

            // 後備：carrierId 取不到時，用 containerMainId 當字串
            if (carrierId.isBlank()) carrierId = String.valueOf(containerMainId);

            if (lotId.isBlank()) {
                log.warn("[Labeling] S020(2003): lotId is blank (containerMainId={}), still sending with carrierId={}", containerMainId, carrierId);
            }

            MqttSendResult r = mqttCommandService.sendS020_2003_TagWait(s020TargetSystem, lotId, carrierId);
            if (r.isSuccess()) {
                log.info("[Labeling] 📤 S020(2003) sent. receiver={}, lotId={}, carrierId={}, TID={}",
                        s020TargetSystem, lotId, carrierId, r.getTid());
            } else {
                log.warn("[Labeling] 📤 S020(2003) send FAILED: {}", r.getMessage());
            }
        } catch (Exception e) {
            log.error("[Labeling] S020 publish failed (site={}, containerId={})", siteCode, containerMainId, e);
        }
    }

    private void resetReportSession() {
        reportSessionActive = false;
        reportSessionSite = null;
        reportSessionContainerId = null;
        reportSessionStartMs = 0L;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}