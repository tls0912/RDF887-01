package com.czkuo.rdf88701.application.monitor.strapping;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.r029.R029OutputCaptureService;
import com.czkuo.rdf88701.application.service.strapping.StrappingAccountingService;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.domain.repository.StrappingPrecheckResultRepository;
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
 * Strapping1Monitor (TID 驅動)
 * 流程：
 *   1) 讀 Site#29 是否有容器 → 有則進入流程
 *   2) 先做補償
 *   3) 設備就緒 (Standby + WaitCmd + Idle)
 *   4) 若無 pendingTid 或容器變更 → 送 S068 取得 tid 並暫存
 *   5) 若有 pendingTid → 用 TID 查 DB
 *        - OK 且未過期，且容器仍在 Site#29 → 寫 PLC + 送 CMD_REQ → 清除 pendingTid
 *        - NG → 記 log 並清除 pendingTid（下次會再送 S068）
 *        - 沒結果且逾時 → 重送 S068（更新 pendingTid/pendingRequestedAt）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Strapping1Monitor {

    private final PlcAccessService plc;
    private final ContainerMainRepository containerMainRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final StrappingAccountingService strappingAccountingService;
    private final MqttCommandService mqttCommandService;
    private final StrappingPrecheckResultRepository precheckResultRepository;
    private final R029OutputCaptureService r029OutputCaptureService;

    // 裝置名稱
    private static final String DEVICE = "PLC-Packer";

    // Bit 區位址
    private static final String B_READY    = "B0200";
    private static final String B_CMD_REQ  = "B0203";
    private static final String B_COMP_ACK = "B0204";
    private static final String B_STANDBY  = "B0800";
    private static final String B_CMD_ACK  = "B0803";
    private static final String B_COMP_REQ = "B0804";

    // Word 區位址
    private static final String W_NO      = "W0398";
    private static final String W_COUNT   = "W039A";
    private static final String W_MODE    = "W039B";
    private static final String W_STATUS  = "W139B";
    private static final String W_RETCODE = "W139E";

    // 任務參數
    private static final String SITE_29 = "Site#29";
    private static final int STRAPPING_COUNT = 2;
    private static final int MODE_STRAPPING  = 1;

    // S068 設定
    @Value("${mqtt.target.s068:ase}")
    private String s068TargetSystem;

    /** S068 OK 可接受的最大片段時間（用於判斷是否過期），預設 120 秒 */
    @Value("${strapping.s068.valid-seconds:120}")
    private int s068ValidSeconds;

    /** 等待 ACK 的超時計時，逾時則重送 S068，預設 20 秒 */
    @Value("${strapping.s068.resend-seconds:20}")
    private int s068ResendSeconds;

    // 狀態紀錄
    private int lastSentNo = -1;
    private boolean commandInProgress = true;

    // 以 TID 為核心的暫存（目前處理中這一顆容器的 S068）
    private String pendingTid = null;
    private Long   pendingContainerId = null;
    private LocalDateTime pendingRequestedAt = null;

    @Scheduled(fixedDelay = 1000)
    public void monitor() {
        try {
            // ====== 先做握手補償（若上一筆還在收尾）======
            Long currentContainer = locationTrackingRepository
                    .findContainerAtLocationName(SITE_29).orElse(0L);
            compensateIfNeeded(currentContainer);

            // ====== 觸發條件：Site#29 有帳 ======
            Optional<Long> containerOpt = locationTrackingRepository.findContainerAtLocationName(SITE_29);
            if (containerOpt.isEmpty()) {
                // 若站點空了，清掉 pendingTid（避免殘留）
                clearPending("site empty");
                return;
            }
            Long containerId = containerOpt.get();

            // 設備就緒
            if (!plc.readBoolean(DEVICE, B_STANDBY)) return;
            int status = plc.readInt32(DEVICE, W_STATUS);
            int deviceStatus = status & 0xF;             // s: 1 Idle / 2 Wait CMD / 3 Processing / 4 Complete
            int runningStatus = (status >> 8) & 0xF;     // r: 1 IDLE / 2 Strapping
            if (deviceStatus != 2 || runningStatus != 1) return;

            // 如果目前沒有 pendingTid，或 pending 的容器與現場不一致 → 重新送 S068
            if (pendingTid == null || !containerId.equals(pendingContainerId)) {
                sendS068AndRemember(containerId);
                return;
            }

            // 有 pendingTid：用 TID 查 DB 結果
            Optional<StrappingPrecheckResult> rOpt = precheckResultRepository.findByTid(pendingTid);
            if (rOpt.isEmpty()) {
                // 還沒回 ACK：判斷是否逾時 → 重送
                if (pendingRequestedAt != null &&
                        Duration.between(pendingRequestedAt, LocalDateTime.now()).getSeconds() >= s068ResendSeconds) {
                    log.warn("[Strapping#1] S068 waiting timeout, resend. containerId={}, oldTid={}",
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
                    log.warn("[Strapping#1] S068 OK expired (age={}s > {}s), containerId={}, tid={}",
                            age.getSeconds(), s068ValidSeconds, pendingContainerId, pendingTid);
                    // 過期重送
                    sendS068AndRemember(containerId);
                    return;
                }

                // 再確認現場容器仍然是同一顆
                Optional<Long> nowOpt = locationTrackingRepository.findContainerAtLocationName(SITE_29);
                if (nowOpt.isEmpty() || !nowOpt.get().equals(pendingContainerId)) {
                    log.warn("[Strapping#1] Container changed during precheck. old={}, now={}, tid={}",
                            pendingContainerId, nowOpt.orElse(null), pendingTid);
                    clearPending("container changed");
                    return;
                }

                // 送 PLC 指令
                lastSentNo = containerId.intValue();
                log.info("[Strapping#1] ▶️ Precheck OK. Sending PLC command. containerId={}, tid={}",
                        containerId, pendingTid);

                plc.writeBoolean(DEVICE, B_READY, true);
                plc.writeInt32(DEVICE, W_NO, containerId.intValue());
                plc.writeInt32(DEVICE, W_COUNT, STRAPPING_COUNT);
                plc.writeInt32(DEVICE, W_MODE, MODE_STRAPPING);
                plc.writeBoolean(DEVICE, B_CMD_REQ, true);

                log.info("[Strapping#1] Sent CMD_REQ");

                // 用過就清掉（或改成 mark consumed）
                precheckResultRepository.deleteByTid(pendingTid);
                clearPending("used");
                return;
            }

            // NG 時記錄並清除狀態，讓下回合可再送。
            if ("NG".equalsIgnoreCase(r.getResult())) {
                log.warn("[Strapping#1] S068 result NG. containerId={}, tid={}, msg={}",
                        pendingContainerId, pendingTid, r.getResultMessage());
                precheckResultRepository.deleteByTid(pendingTid);
                clearPending("NG");
                return;
            }

            // 其他狀態（理論上不會）
            log.warn("[Strapping#1] Unexpected S068 result={}, tid={}", r.getResult(), pendingTid);

        } catch (Exception e) {
            log.error("[Strapping#1] Exception occurred", e);
        }
    }

    private void sendS068AndRemember(Long containerId) {
        log.info("[Strapping#1] 🔎 Send S068 precheck. containerId={}", containerId);
        MqttSendResult send = mqttCommandService.sendS068(s068TargetSystem);
        if (send.isSuccess() && send.getTid() != null) {
            this.pendingTid = send.getTid();
            this.pendingContainerId = containerId;
            this.pendingRequestedAt = LocalDateTime.now();
            log.info("[Strapping#1] S068 sent. containerId={}, tid={}", containerId, pendingTid);
            // 如果想預建 PENDING，可在這裡插入 DB；但目前簡化方案不需要
        } else {
            log.error("[Strapping#1] ❌ Failed to send S068. containerId={}, reason={}",
                    containerId, send.getMessage());
            clearPending("send failed");
        }
    }

    private void clearPending(String reason) {
        if (pendingTid != null || pendingContainerId != null) {
            //log.debug("[Strapping#1] Clear pending due to {}. tid={}, containerId={}",
//                    reason, pendingTid, pendingContainerId);
        }
        this.pendingTid = null;
        this.pendingContainerId = null;
        this.pendingRequestedAt = null;
    }

    /**
     * 三段握手補償
     */
    private void compensateIfNeeded(Long expectedContainerId) {
        boolean ackIssued   = plc.readBoolean(DEVICE, B_CMD_ACK);
        boolean compIssued  = plc.readBoolean(DEVICE, B_COMP_REQ);
        boolean compAckSent = plc.readBoolean(DEVICE, B_COMP_ACK);
        int strappingNo     = plc.readInt32(DEVICE, W_NO);
        int retCode         = plc.readInt32(DEVICE, W_RETCODE);

        //log.debug("[Strapping#1] ⏪ Compensation check, CMD_ACK={}, COMP_REQ={}, COMP_ACK={}, RetCode=0x{}",
//                ackIssued, compIssued, compAckSent, Integer.toHexString(retCode));

        // 回收 CMD_REQ
        if (ackIssued) {
            plc.writeBoolean(DEVICE, B_CMD_REQ, false);
            log.info("[Strapping] Recalled CMD_REQ (PLC still holding CMD_ACK)");
            return;
        }

        // 發送 COMP_ACK
        if (compIssued && !compAckSent) {
            switch (retCode) {
                case 0x0100 -> {
                    log.info("[Strapping#1] Previous command success");

                    if (expectedContainerId != null && expectedContainerId.intValue() == strappingNo) {
                        strappingAccountingService.markStrappingCompleted(expectedContainerId, SITE_29);

                        boolean ok = containerMainRepository.close(expectedContainerId);
                        if (!ok) {
                            log.warn("[Strapping#1] close container failed (maybe already closed). containerId={}", expectedContainerId);
                        } else {
                            log.info("[Strapping#1] container closed. containerId={}", expectedContainerId);
                        }

                        try {
                            r029OutputCaptureService.recordStrappingIfBelongs(expectedContainerId);
                        } catch (Exception ex) {
                            log.error("[Strapping#1] recordStrappingIfBelongs() failed. containerId={}", expectedContainerId, ex);
                        }
                    } else {
                        log.warn("[Strapping#1] success retCode but container mismatch. siteContainerId={}, plcNo={}",
                                expectedContainerId, strappingNo);
                    }
                }
                case 0x0800 -> log.warn("[Strapping#1] ⚠️ Previous command aborted");
                case 0x0F00 -> log.error("[Strapping#1] ❌ Previous command failed");
                default -> log.info("[Strapping#1] Waiting for valid RetCode...");
            }
            plc.writeBoolean(DEVICE, B_COMP_ACK, true);
            log.info("[Strapping#1] Sent COMP_ACK (PLC still holding COMP_REQ)");
            return;
        }

        // 關閉 COMP_ACK
        if (!compIssued && compAckSent) {
            plc.writeBoolean(DEVICE, B_COMP_ACK, false);
            commandInProgress = false;
            //log.debug("[Strapping#1] Reset COMP_ACK (PLC cleared COMP_REQ)");
        }
    }
}
