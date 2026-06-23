package com.czkuo.rdf88701.application.monitor.strapping;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.r029.R029OutputCaptureService;
import com.czkuo.rdf88701.application.service.strapping.StrappingAccountingService;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.domain.repository.StrappingPrecheckResultRepository;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.entity.StrappingPrecheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Strapping2Monitor (TID 驅動)
 * - 觸發條件：Transfer#8 有容器 且 位置在 VIRTUAL#13
 * - 先發 S068（打帶前狀態確認），用 TID 由 DB 取回結果為 OK 才送 PLC
 * - PLC 三段握手：CMD_REQ -> CMD_ACK、COMP_REQ -> COMP_ACK
 * - 成功後呼叫 StrappingAccountingService 註記完成
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Strapping2Monitor {

    private final PlcAccessService plc;
    private final ContainerMainRepository containerMainRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final StrappingAccountingService strappingAccountingService;
    private final TransferStatusCache transferStatusCache;

    // 新增：S068 相關
    private final MqttCommandService mqttCommandService;
    private final StrappingPrecheckResultRepository precheckResultRepository;

    private final R029OutputCaptureService r029OutputCaptureService;

    // 裝置
    private static final String DEVICE = "PLC-Packer";

    // ===== Strapping#2 位址 =====
    // Write Bit
    private static final String B_READY    = "B0208";
    private static final String B_CMD_REQ  = "B020B";
    private static final String B_COMP_ACK = "B020C";
    // Read Bit
    private static final String B_STANDBY  = "B0808";
    private static final String B_CMD_ACK  = "B080B";
    private static final String B_COMP_REQ = "B080C";
    // Write Word
    private static final String W_NO    = "W03A0";
    private static final String W_COUNT = "W03A2";
    private static final String W_MODE  = "W03A3";
    // Read Word
    private static final String W_STATUS  = "W13A3";
    private static final String W_RETCODE = "W13A6";

    // ===== 業務條件 =====
    private static final long   TRANSFER8_ID   = 8L;
    private static final String TRANSFER8_NAME = "Transfer#8";
    private static final String VIRTUAL_13     = "VIRTUAL#13";
    // 依 PLC 對應：VIRTUAL#13 的物理 Level（請依實際修改）
    private static final int LEVEL_V13 = 213;

    // 任務參數
    private static final int STRAPPING_COUNT  = 2; // 次數
    private static final int MODE_STRAPPING   = 1; // 1: need to strapping

    // ===== S068 設定 =====
    @Value("${mqtt.target.s068:ase}")
    private String s068TargetSystem;

    /** S068 OK 可接受的最大片段時間（判斷是否過期），預設 120 秒 */
    @Value("${strapping.s068.valid-seconds:120}")
    private int s068ValidSeconds;

    /** 等待 ACK 超時計時（秒），逾時則重送 S068，預設 20 秒 */
    @Value("${strapping.s068.resend-seconds:20}")
    private int s068ResendSeconds;

    // 狀態記錄
    private int lastSentNo = -1;

    // 以 TID 為核心的暫存（目前處理中的這一顆容器的 S068）
    private String        pendingTid = null;
    private Long          pendingContainerId = null;
    private LocalDateTime pendingRequestedAt = null;

    @Scheduled(fixedDelay = 1000)
    public void monitor() {
        try {
            // ====== 先做握手補償（若上一筆還在收尾）======
            Long currentContainer = locationTrackingRepository
                    .findContainerOnTransfer(TRANSFER8_ID).orElse(0L);
            compensateIfNeeded(currentContainer);

            // ====== 觸發條件：Transfer#8 有帳 ======
            Optional<Long> containerOpt = locationTrackingRepository.findContainerOnTransfer(TRANSFER8_ID);
            if (containerOpt.isEmpty()) {
                //log.debug("[Strapping#2] {} 無容器，略過", TRANSFER8_NAME);
                clearPending("site empty");
                return;
            }
            Long containerId = containerOpt.get();

            // ====== 位置需在 VIRTUAL#13 ======
            TransferDeviceStatus tds = transferStatusCache.getLatest(TRANSFER8_NAME);
            if (tds == null || !tds.isValidAndComplete(3)) {
                //log.debug("[Strapping#2] 轉盤狀態無效（{}）", TRANSFER8_NAME);
                return;
            }
            Integer level = safeGetLevel(tds);
            if (level == null || level != LEVEL_V13) {
                //log.debug("[Strapping#2] {} 目前不在 {}（level={}）", TRANSFER8_NAME, VIRTUAL_13, level);
                return;
            }

            // ====== 裝置需就緒 → Standby + WaitCMD + IDLE ======
            if (!plc.readBoolean(DEVICE, B_STANDBY)) {
                //log.debug("[Strapping#2] 裝置未 Standby");
                return;
            }
            int status = plc.readInt32(DEVICE, W_STATUS);
            int deviceStatus  = status & 0xF;         // s: 1 Idle / 2 Wait CMD / 3 Processing / 4 Complete
            int runningStatus = (status >> 8) & 0xF;  // r: 1 IDLE / 2 Strapping
            if (deviceStatus != 2 || runningStatus != 1) {
                //log.debug("[Strapping#2] 非 WaitCMD+IDLE（s={}, r={}）", deviceStatus, runningStatus);
                return;
            }

            // ====== S068：若沒有 pendingTid 或容器變更 → 送 S068 ======
            if (pendingTid == null || !containerId.equals(pendingContainerId)) {
                sendS068AndRemember(containerId);
                return;
            }

            // ====== 已有 pendingTid：用 TID 查 DB 結果 ======
            Optional<StrappingPrecheckResult> rOpt = precheckResultRepository.findByTid(pendingTid);
            if (rOpt.isEmpty()) {
                // 還沒回 ACK：判斷是否逾時 → 重送
                if (pendingRequestedAt != null &&
                        Duration.between(pendingRequestedAt, LocalDateTime.now()).getSeconds() >= s068ResendSeconds) {
                    log.warn("[Strapping#2] S068 waiting timeout, resend. containerId={}, oldTid={}",
                            pendingContainerId, pendingTid);
                    sendS068AndRemember(containerId);
                }
                return;
            }

            StrappingPrecheckResult r = rOpt.get();
            if ("OK".equalsIgnoreCase(r.getResult())) {
                // 檢查是否過期
                Duration age = Duration.between(r.getCreatedTime(), LocalDateTime.now());
                if (age.getSeconds() > s068ValidSeconds) {
                    log.warn("[Strapping#2] S068 OK expired (age={}s > {}s), containerId={}, tid={}",
                            age.getSeconds(), s068ValidSeconds, pendingContainerId, pendingTid);
                    sendS068AndRemember(containerId); // 過期重送
                    return;
                }

                // 再確認：容器仍在 Transfer#8 且仍在 VIRTUAL#13
                Optional<Long> nowOpt = locationTrackingRepository.findContainerOnTransfer(TRANSFER8_ID);
                TransferDeviceStatus nowTds = transferStatusCache.getLatest(TRANSFER8_NAME);
                Integer nowLevel = (nowTds != null) ? safeGetLevel(nowTds) : null;
                if (nowOpt.isEmpty() || !nowOpt.get().equals(pendingContainerId) || nowLevel == null || nowLevel != LEVEL_V13) {
                    log.warn("[Strapping#2] Container/Position changed during precheck. oldId={}, nowId={}, oldLv={}, nowLv={}, tid={}",
                            pendingContainerId, nowOpt.orElse(null), level, nowLevel, pendingTid);
                    clearPending("container/position changed");
                    return;
                }

                // ====== 送 PLC 指令 ======
                lastSentNo = containerId.intValue();
                log.info("[Strapping#2] ▶️ Precheck OK. Sending PLC command. containerId={}, tid={}",
                        containerId, pendingTid);

                plc.writeBoolean(DEVICE, B_READY, true);
                plc.writeInt32(DEVICE, W_NO,    containerId.intValue());
                plc.writeInt32(DEVICE, W_COUNT, STRAPPING_COUNT);
                plc.writeInt32(DEVICE, W_MODE,  MODE_STRAPPING);
                plc.writeBoolean(DEVICE, B_CMD_REQ, true);

                log.info("[Strapping#2] Sent CMD_REQ");

                // 用過就清掉（或改 mark consumed）
                precheckResultRepository.deleteByTid(pendingTid);
                clearPending("used");
                return;
            }

            if ("NG".equalsIgnoreCase(r.getResult())) {
                log.warn("[Strapping#2] S068 result NG. containerId={}, tid={}, msg={}",
                        pendingContainerId, pendingTid, r.getResultMessage());
                precheckResultRepository.deleteByTid(pendingTid);
                clearPending("NG");
                return;
            }

            log.warn("[Strapping#2] Unexpected S068 result={}, tid={}", r.getResult(), pendingTid);

        } catch (Exception ex) {
            log.error("[Strapping#2] Exception", ex);
        }
    }

    private void sendS068AndRemember(Long containerId) {
        log.info("[Strapping#2] 🔎 Send S068 precheck. containerId={}", containerId);
        MqttSendResult send = mqttCommandService.sendS068(s068TargetSystem);
        if (send.isSuccess() && send.getTid() != null) {
            this.pendingTid = send.getTid();
            this.pendingContainerId = containerId;
            this.pendingRequestedAt = LocalDateTime.now();
            log.info("[Strapping#2] S068 sent. containerId={}, tid={}", containerId, pendingTid);
            // 如需預先建立 PENDING 記錄，可於此插入 DB；此處維持精簡不建檔
        } else {
            log.error("[Strapping#2] ❌ Failed to send S068. containerId={}, reason={}",
                    containerId, send.getMessage());
            clearPending("send failed");
        }
    }

    private void clearPending(String reason) {
        if (pendingTid != null || pendingContainerId != null) {
            //log.debug("[Strapping#2] Clear pending due to {}. tid={}, containerId={}",
//                    reason, pendingTid, pendingContainerId);
        }
        this.pendingTid = null;
        this.pendingContainerId = null;
        this.pendingRequestedAt = null;
    }

    /** 三段握手補償流程 */
    private void compensateIfNeeded(Long expectedContainerId) {
        boolean cmdAck   = plc.readBoolean(DEVICE, B_CMD_ACK);
        boolean compReq  = plc.readBoolean(DEVICE, B_COMP_REQ);
        boolean compAck  = plc.readBoolean(DEVICE, B_COMP_ACK);
        int strappingNo  = plc.readInt32(DEVICE, W_NO);
        int retCode      = plc.readInt32(DEVICE, W_RETCODE);

        //log.debug("[Strapping#2] ⏪ Compensate check: CMD_ACK={}, COMP_REQ={}, COMP_ACK={}, Ret=0x{}",
//                cmdAck, compReq, compAck, Integer.toHexString(retCode));

        // 1) PLC 已拉起 CMD_ACK → 我方回收 CMD_REQ
        if (cmdAck) {
            if (plc.readBoolean(DEVICE, B_CMD_REQ)) {
                plc.writeBoolean(DEVICE, B_CMD_REQ, false);
                log.info("[Strapping#2] Recall CMD_REQ (PLC holds CMD_ACK)");
            }
            return;
        }

        // 2) PLC 發出 COMP_REQ → 我方送 COMP_ACK，並依 RetCode 記錄結果
        if (compReq && !compAck) {
            switch (retCode) {
                case 0x0100 -> {
                    log.info("[Strapping#2] ✔ Command Success");

                    if (expectedContainerId != null && expectedContainerId.intValue() == strappingNo) {
                        strappingAccountingService.markStrappingCompleted(expectedContainerId, TRANSFER8_NAME);

                        boolean ok = containerMainRepository.close(expectedContainerId);
                        if (!ok) {
                            log.warn("[Strapping#2] close container failed (maybe already closed). containerId={}", expectedContainerId);
                        } else {
                            log.info("[Strapping#2] container closed. containerId={}", expectedContainerId);
                        }

                        try {
                            r029OutputCaptureService.recordStrappingIfBelongs(expectedContainerId);
                        } catch (Exception ex) {
                            log.error("[Strapping#2] recordStrappingIfBelongs() failed. containerId={}", expectedContainerId, ex);
                        }
                    } else {
                        log.warn("[Strapping#2] 回報 No({}) 與期望容器({}) 不一致，跳過完成標記",
                                strappingNo, expectedContainerId);
                    }
                }
                case 0x0800 -> log.warn("[Strapping#2] ⚠ Command Abort");
                case 0x0F00 -> log.error("[Strapping#2] ❌ Command Fail");
                default     -> log.info("[Strapping#2] 等待有效 RetCode...");
            }
            plc.writeBoolean(DEVICE, B_COMP_ACK, true);
            log.info("[Strapping#2] Sent COMP_ACK");
            return;
        }

        // 3) PLC 已清除 COMP_REQ → 我方亦需清除 COMP_ACK
        if (!compReq && compAck) {
            plc.writeBoolean(DEVICE, B_COMP_ACK, false);
            //log.debug("[Strapping#2] Reset COMP_ACK");
        }
    }

    private Integer safeGetLevel(TransferDeviceStatus ds) {
        try { return ds.getLevel(); } catch (Throwable ignore) { return null; }
    }
}
