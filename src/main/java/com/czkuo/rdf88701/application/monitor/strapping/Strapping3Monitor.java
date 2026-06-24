package com.czkuo.rdf88701.application.monitor.strapping;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.strapping.StrappingAccountingService;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
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
 * Strapping3Monitor (Transfer#9, VIRTUAL#16)
 * 觸發：Transfer#9 productPresent==true 且 Level==VIRTUAL#16
 * 條件：Strapping#3 Standby + WaitCMD + IDLE
 * 流程：先發 S068(取 TID)→DB 查 TID 結果 OK→下 PLC；含 CMD/COMP 三段握手與成功後清帳
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Strapping3Monitor {

    private final PlcAccessService plc;
    private final ContainerMainRepository containerMainRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final StrappingAccountingService strappingAccountingService;
    private final TransferStatusCache transferStatusCache;

    // S068 相關
    private final MqttCommandService mqttCommandService;
    private final StrappingPrecheckResultRepository precheckResultRepository;
    private final RobotInR029LotRepository r029LotRepo;
    private final RobotR008TaskRepository r008TaskRepository;
    // 裝置
    private static final String DEVICE = "PLC-Packer";

    // ===== Strapping#3 位址（#2 基礎 +0x08）=====
    // Write Bit
    private static final String B_READY = "B0210";
    private static final String B_CMD_REQ = "B0213";
    private static final String B_COMP_ACK = "B0214";
    // Read Bit
    private static final String B_STANDBY = "B0810";
    private static final String B_CMD_ACK = "B0813";
    private static final String B_COMP_REQ = "B0814";
    // Write Word
    private static final String W_NO = "W03A8";
    private static final String W_COUNT = "W03AA";
    private static final String W_MODE = "W03AB";
    // Read Word
    private static final String W_STATUS = "W13AB";
    private static final String W_RETCODE = "W13AE";

    // ===== Transfer 條件 =====
    private static final long TRANSFER9_ID = 9L;
    private static final String TRANSFER9_NAME = "Transfer#9";
    private static final String VIRTUAL_16 = "VIRTUAL#16";
    // 目前以 LEVEL_V16 表示 VIRTUAL#16 的 PLC level。
    private static final int LEVEL_V16 = 216;

    // 任務參數
    private static int STRAPPING_COUNT = 1; // 次數
    private static int MODE_STRAPPING = 1; // 1: need to strapping

    // ===== S068 設定 =====
    @Value("${mqtt.target.s068:ase}")
    private String s068TargetSystem;

    /**
     * S068 OK 可接受的最大片段時間（秒）
     */
    @Value("${strapping.s068.valid-seconds:120}")
    private int s068ValidSeconds;

    /**
     * 等待 ACK 超時計時（秒），逾時則重送 S068
     */
    @Value("${strapping.s068.resend-seconds:20}")
    private int s068ResendSeconds;

    // 以 TID 為核心的暫存（本工位目前處理中的 S068）
    private String pendingTid = null;
    private Long pendingContainerId = null;
    private LocalDateTime pendingRequestedAt = null;

    @Scheduled(fixedDelay = 1000)
    public void monitor() {
        try {
            // 取 TR9 狀態（present & 在 VIRTUAL#16）
            TransferDeviceStatus tds = transferStatusCache.getLatest(TRANSFER9_NAME);
            if (tds == null || !tds.isValidAndComplete(3)) {
                //log.debug("[Strapping#3] {} 狀態無效", TRANSFER9_NAME);
                return;
            }

            // 先做握手補償（若上一筆還在收尾）
            Long currentContainer = locationTrackingRepository
                    .findContainerOnTransfer(TRANSFER9_ID).orElse(0L);
            compensateIfNeeded(currentContainer);

            boolean present = tds.isProductPresent();
            Integer level = safeGetLevel(tds);
            if (!present || level == null || level != LEVEL_V16) {
                //log.debug("[Strapping#3] 條件未達：present={} , level={}", present, level);
                return;
            }

            Long containerId = -1L;
            String containerName = tds.getProductId();
            if (!containerName.trim().isEmpty()) {
                Optional<ContainerMain> opt = containerMainRepository.findByAliasCode(containerName);
                if (opt.isPresent()) {
                    ContainerMain cm = opt.get();
                    containerId = cm.getId();
                }
            }

            if (containerId < 0) {
                //log.debug("[Strapping#3] {} 無容器帳，略過", TRANSFER9_NAME);
                clearPending("site empty");
                return;
            }
            // 需能取到容器帳（用來寫 W_NO 與完成清帳）
            // Optional<Long> containerOpt = locationTrackingRepository.findContainerOnTransfer(TRANSFER9_ID);
            // if (containerOpt.isEmpty()) {
            //     log.debug("[Strapping#3] {} 無容器帳，略過", TRANSFER9_NAME);
            //     clearPending("site empty");
            //     return;
            // }
            // Long containerId = containerOpt.get();

            // 裝置需就緒：Standby + WaitCMD + IDLE
            if (!plc.readBoolean(DEVICE, B_STANDBY)) return;
            int status = plc.readInt32(DEVICE, W_STATUS);
            int s = status & 0xF;         // 1 Idle / 2 Wait CMD / 3 Processing / 4 Complete
            int r = (status >> 8) & 0xF;  // 1 IDLE / 2 Strapping
            if (s != 2 || r != 1) return;

            // S068：若沒有 pendingTid 或容器變更 → 送 S068
            if (pendingTid == null || !containerId.equals(pendingContainerId)) {
                sendS068AndRemember(containerId);
                return;
            }

            // 已有 pendingTid：用 TID 查 DB 結果
            Optional<StrappingPrecheckResult> rOpt = precheckResultRepository.findByTid(pendingTid);
            if (rOpt.isEmpty()) {
                // 還沒回 ACK：逾時則重送
                if (pendingRequestedAt != null &&
                        Duration.between(pendingRequestedAt, LocalDateTime.now()).getSeconds() >= s068ResendSeconds) {
                    log.warn("[Strapping#3] S068 waiting timeout, resend. containerId={}, oldTid={}",
                            pendingContainerId, pendingTid);
                    sendS068AndRemember(containerId);
                }
                return;
            }

            StrappingPrecheckResult precheck = rOpt.get();
            if ("OK".equalsIgnoreCase(precheck.getResult())) {
                // 檢查是否過期
                Duration age = Duration.between(precheck.getCreatedTime(), LocalDateTime.now());
                if (age.getSeconds() > s068ValidSeconds) {
                    log.warn("[Strapping#3] S068 OK expired (age={}s > {}s), containerId={}, tid={}",
                            age.getSeconds(), s068ValidSeconds, pendingContainerId, pendingTid);
                    sendS068AndRemember(containerId); // 過期重送
                    return;
                }

                // 再確認：容器仍在 TR9 且仍在 VIRTUAL#16
                // Optional<Long> nowOpt = locationTrackingRepository.findContainerOnTransfer(TRANSFER9_ID);
                // TransferDeviceStatus nowTds = transferStatusCache.getLatest(TRANSFER9_NAME);
                // Integer nowLevel = (nowTds != null) ? safeGetLevel(nowTds) : null;
                // if (nowOpt.isEmpty() || !nowOpt.get().equals(pendingContainerId) || nowLevel == null || nowLevel != LEVEL_V16) {
                //     log.warn("[Strapping#3] Container/Position changed during precheck. oldId={}, nowId={}, oldLv={}, nowLv={}, tid={}",
                //             pendingContainerId, nowOpt.orElse(null), level, nowLevel, pendingTid);
                //     clearPending("container/position changed");
                //     return;
                // }

                TransferDeviceStatus nowTds = transferStatusCache.getLatest(TRANSFER9_NAME);
                Integer nowLevel = (nowTds != null) ? safeGetLevel(nowTds) : null;
                if (nowLevel == null || nowLevel != LEVEL_V16) {
                    log.warn("[Strapping#3] Container/Position changed during precheck. oldId={}, oldLv={}, nowLv={}, tid={}",
                            pendingContainerId, level, nowLevel, pendingTid);
                    clearPending("container/position changed");
                    return;
                }
                var check = r008TaskRepository.findBinTypeByCarrierId(containerName);
                //var check = r029LotRepo.findIdByCarrierId(containerName);
                MODE_STRAPPING = check.isEmpty() || "B".equals(check.get(0)) ? 1 : 0;
                STRAPPING_COUNT = check.isEmpty() || "B".equals(check.get(0)) ? 1 : 0;
                // 發送命令（W_NO = containerId）
                log.info("[Strapping#3] ▶️ Precheck OK. Send CMD, TR9@V16, CONTAINER_ID={}, tid={}", containerId, pendingTid);
                plc.writeBoolean(DEVICE, B_READY, true);
                plc.writeInt32(DEVICE, W_NO, containerId.intValue());
                plc.writeInt32(DEVICE, W_COUNT, STRAPPING_COUNT);
                plc.writeInt32(DEVICE, W_MODE, MODE_STRAPPING);
                plc.writeBoolean(DEVICE, B_CMD_REQ, true);
                log.info("[Strapping#3] Sent CMD_REQ");

                // 用過就清掉（或改 mark consumed）
                precheckResultRepository.deleteByTid(pendingTid);
                clearPending("used");
                return;
            }

            if ("NG".equalsIgnoreCase(precheck.getResult())) {
                log.warn("[Strapping#3] S068 result NG. containerId={}, tid={}, msg={}",
                        pendingContainerId, pendingTid, precheck.getResultMessage());
                precheckResultRepository.deleteByTid(pendingTid);
                clearPending("NG");
                return;
            }

            log.warn("[Strapping#3] Unexpected S068 result={}, tid={}", precheck.getResult(), pendingTid);

        } catch (Exception ex) {
            log.error("[Strapping#3] Exception", ex);
        }
    }

    private void sendS068AndRemember(Long containerId) {
        log.info("[Strapping#3] 🔎 Send S068 precheck. containerId={}", containerId);
        MqttSendResult send = mqttCommandService.sendS068(s068TargetSystem);
        if (send.isSuccess() && send.getTid() != null) {
            this.pendingTid = send.getTid();
            this.pendingContainerId = containerId;
            this.pendingRequestedAt = LocalDateTime.now();
            log.info("[Strapping#3] S068 sent. containerId={}, tid={}", containerId, pendingTid);
        } else {
            log.error("[Strapping#3] ❌ Failed to send S068. containerId={}, reason={}",
                    containerId, send.getMessage());
            clearPending("send failed");
        }
    }

    private void clearPending(String reason) {
        if (pendingTid != null || pendingContainerId != null) {
            //log.debug("[Strapping#3] Clear pending due to {}. tid={}, containerId={}",
//                    reason, pendingTid, pendingContainerId);
        }
        this.pendingTid = null;
        this.pendingContainerId = null;
        this.pendingRequestedAt = null;
    }

    /**
     * 三段握手補償流程
     */
    private void compensateIfNeeded(Long expectedContainerId) {
        boolean cmdAck = plc.readBoolean(DEVICE, B_CMD_ACK);
        boolean compReq = plc.readBoolean(DEVICE, B_COMP_REQ);
        boolean compAck = plc.readBoolean(DEVICE, B_COMP_ACK);
        int strNo = plc.readInt32(DEVICE, W_NO);
        int ret = plc.readInt32(DEVICE, W_RETCODE);

        //log.debug("[Strapping#3] ⏪ Compensate check: CMD_ACK={}, COMP_REQ={}, COMP_ACK={}, Ret=0x{}",
//                cmdAck, compReq, compAck, Integer.toHexString(ret));

        // 1) PLC 已拉起 CMD_ACK → 回收 CMD_REQ
        if (cmdAck) {
            if (plc.readBoolean(DEVICE, B_CMD_REQ)) {
                plc.writeBoolean(DEVICE, B_CMD_REQ, false);
                log.info("[Strapping#3] Recall CMD_REQ (PLC holds CMD_ACK)");
            }
            return;
        }

        // 2) PLC 發出 COMP_REQ → 我方送 COMP_ACK，並依 RetCode 處理
        if (compReq && !compAck) {
            switch (ret) {
                case 0x0100 -> {
                    log.info("[Strapping#3] Command Success");
                    if (expectedContainerId != null && expectedContainerId.intValue() == strNo) {
                        strappingAccountingService.markStrappingCompleted(expectedContainerId, VIRTUAL_16);
                    } else {
                        log.warn("[Strapping#3] 回報 No({}) 與期望容器({}) 不一致，跳過完成標記", strNo, expectedContainerId);
                    }
                }
                case 0x0800 -> log.warn("[Strapping#3] Command Abort");
                case 0x0F00 -> log.error("[Strapping#3] Command Fail");
                default -> log.info("[Strapping#3] 等待有效 RetCode...");
            }
            plc.writeBoolean(DEVICE, B_COMP_ACK, true);
            log.info("[Strapping#3] Sent COMP_ACK");
            return;
        }

        // 3) PLC 已清除 COMP_REQ → 我方也清除 COMP_ACK
        if (!compReq && compAck) {
            plc.writeBoolean(DEVICE, B_COMP_ACK, false);
            //log.debug("[Strapping#3] Reset COMP_ACK");
        }
    }

    private Integer safeGetLevel(TransferDeviceStatus ds) {
        try {
            return ds.getLevel();
        } catch (Throwable ignore) {
            return null;
        }
    }
}
