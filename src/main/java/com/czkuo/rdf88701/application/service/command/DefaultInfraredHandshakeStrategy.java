package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.application.assembler.InfraredWordCommandAssembler;
import com.czkuo.rdf88701.application.assembler.PlcInfraredWordCommand;
import com.czkuo.rdf88701.application.service.task.InfraredTaskLifecycleService;
import com.czkuo.rdf88701.domain.plc.state.infrared.*;
import com.czkuo.rdf88701.domain.repository.InfraredHandshakeContextRepository;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcInfraredBitWriter;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcInfraredWordWriter;
import com.czkuo.rdf88701.infra.cache.SiteStatusCache;
import com.czkuo.rdf88701.infra.entity.InfraredTask;
import com.czkuo.rdf88701.infra.event.model.plc.infrared.InfraredTaskCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 預設 Infrared 握手策略
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultInfraredHandshakeStrategy implements InfraredHandshakeStrategy {

    private final ApplicationEventPublisher eventPublisher;
    private final InfraredWordCommandAssembler assembler;
    private final PlcInfraredWordWriter wordWriter;
    private final PlcInfraredBitWriter bitWriter;
    private final InfraredHandshakeContextRepository contextRepository;
    private final InfraredTaskLifecycleService taskLifecycleService;

    private final SiteStatusCache siteStatusCache;

    @Override
    public void tick(InfraredTask task, InfraredDeviceStatus status, InfraredCommandStatus commandStatus) {
        Long infraredId = task.getInfraredId();
        Long taskId = task.getId();
        String infraredName = "IR#" + infraredId;

        InfraredHandshakeContext ctx = contextRepository.getOrInit(taskId, infraredName);
        InfraredHandshakePhase phase = ctx.getPhase();

        // 始終維持 Ready = ON（必要時可加條件）
        sendReady(task);

        switch (phase) {
            case NONE -> {
                // === 補償判斷（順序重要）===
                if (isAckIssued(status)) {
                    ctx.moveTo(InfraredHandshakePhase.ACK_RECEIVED);
                } else if (isCompletionAcked(commandStatus)) {
                    ctx.moveTo(InfraredHandshakePhase.RESPONDED_COMPLETION);
                } else if (isJobHandling(status)) {
                    if (hasCommandBeenSent(commandStatus, task)) {
                        if (isTerminalStatus(task)) {
                            log.warn("[HS] 補償 → RESPONDED_COMPLETION（JobHandling=true & ID 一致）");
                            ctx.moveTo(InfraredHandshakePhase.RESPONDED_COMPLETION);
                        } else {
                            log.warn("[HS] 補償 → IN_PROGRESS（JobHandling=true & ID 一致）");
                            ctx.moveTo(InfraredHandshakePhase.IN_PROGRESS);
                            taskLifecycleService.markInProgress(task);
                        }
                    } else {
                        if (isCompletionRequested(status)) {
                            int transferNo = commandStatus != null ? commandStatus.getInfraredNo() : -1;
                            int retCode = status.getReturnCodeValue();
                            String desc = interpretReturnCode(retCode);

                            log.info("[HS] 補償：Infrared '{}' 舊任務#{} 收到 CompletionReq，code=0x{} ({}) → 回送 CompAck 結束舊任務",
                                    infraredId, transferNo, Integer.toHexString(retCode), desc);

                            setCompAck(infraredId, true);
                            ctx.moveTo(InfraredHandshakePhase.RESPONDED_COMPLETION);
                            return;
                        }

                        //log.debug("[HS] 補償等待：JobHandling=true 且 MeasureNo 不一致，尚未出現 CompletionReq");
                        return;
                    }
                } else if (isCompletionRequested(status)) {
                    ctx.moveTo(InfraredHandshakePhase.COMPLETION_RECEIVED);
                } else if (isTerminalStatus(task)) {
                    ctx.moveTo(InfraredHandshakePhase.RESPONDED_COMPLETION);
                } else {
                    // ============================
                    // 只能在「Wait CMD」才發送命令
                    // ============================
                    if (isWaitingForCommand(status)) {
                        sendCommand(task);
                        ctx.moveTo(InfraredHandshakePhase.CMD_SENT);
                        taskLifecycleService.markDispatched(task);
                    } else {
                        // 不符合 Wait CMD → 本輪不送，避免提早下指令
                        //log.debug("[HS] Infrared '{}' 尚未進入 Wait CMD（standby={}, deviceStatus={}, ack={}）→ 略過發送",
//                                infraredId, status.isInfraredStandby(),
//                                status.getDeviceStatusCode(), status.isMeasureCmdAck());
                        return;
                    }
                }
            }

            case CMD_SENT -> {
                if (isAckIssued(status)) {
                    ctx.moveTo(InfraredHandshakePhase.ACK_RECEIVED);
                } else if (ctx.isTimeout(30)) {
                    log.warn("[HS] Infrared '{}' 任務#{} 指令超時，重送", infraredId, taskId);
                    resetCmdReq(infraredId);
                    // 重送前再次確認仍處於 Wait CMD
                    if (isWaitingForCommand(status)) {
                        sendCommand(task);
                        ctx.resetTimeout();
                    } else {
                        log.warn("[HS] Infrared '{}' 超時後已非 Wait CMD，暫不重送（等待下一輪）", infraredId);
                        ctx.moveTo(InfraredHandshakePhase.NONE);
                    }
                }
            }

            case ACK_RECEIVED -> {
                resetCmdReq(infraredId);
                ctx.moveTo(InfraredHandshakePhase.CMD_REQ_CLEARED);
            }

            case CMD_REQ_CLEARED -> {
                ctx.moveTo(InfraredHandshakePhase.IN_PROGRESS);
                taskLifecycleService.markInProgress(task);
            }

            case IN_PROGRESS -> {
                if (isCompletionRequested(status)) {
                    ctx.moveTo(InfraredHandshakePhase.COMPLETION_RECEIVED);
                } else if (ctx.isTimeout(300)) {
                    log.warn("[HS] Infrared '{}' 任務#{} 執行超時補償：重置 CompAck 並回到 NONE", infraredId, taskId);
                    resetCompAck(infraredId);
                    ctx.resetTimeout();
                    ctx.moveTo(InfraredHandshakePhase.NONE);
                }
            }

            case COMPLETION_RECEIVED -> {
                int retCode = status.getReturnCode();
                String desc = interpretReturnCode(retCode);

                log.info("[HS] Infrared '{}' 任務#{} 回傳碼：0x{} - {}", infraredId, taskId,
                        Integer.toHexString(retCode), desc);

                // 帶入測量成果（兩側高度、PLC 層數）
                eventPublisher.publishEvent(
                        new InfraredTaskCompletedEvent(
                                this,
                                task,
                                status.getProductHeight1(),
                                status.getProductHeight2(),
                                status.getProductQuantity(),
                                retCode,
                                desc
                        )
                );

                setCompAck(infraredId, true);
                ctx.moveTo(InfraredHandshakePhase.RESPONDED_COMPLETION);
            }

            case RESPONDED_COMPLETION -> {
                if (!isCompletionRequested(status)) {
                    setCompAck(infraredId, false);
                    ctx.moveTo(InfraredHandshakePhase.COMPLETION_CONFIRMED);
                }
            }

            case COMPLETION_CONFIRMED -> {
                // 僅在任務終態時才補寫 done_time，避免 RETRY 被壓成 done
                if (shouldWriteDone(task)) {
                    markTaskAsDone(task);
                    ctx.moveTo(InfraredHandshakePhase.DONE);
                } else {
                    log.info("[HS] {} 任務#{} 非終態({})，不寫 done_time，回到 NONE 等待後續",
                            infraredName, taskId, task.getTaskStatus());
                    resetCompAck(infraredId); // 保險
                    ctx.moveTo(InfraredHandshakePhase.NONE);
                }
            }

            case FAILED -> taskLifecycleService.markFailed(task);
            case DONE -> { /* no-op */ }
        }

        contextRepository.save(ctx);
    }

    // ====== 內部工具 ======

    private void sendReady(InfraredTask task) {
        Long sensorId = task.getInfraredId();
        bitWriter.writeInfraredReady(sensorId, true);
    }

    private void sendCommand(InfraredTask task) {
        Long sensorId = task.getInfraredId();
        PlcInfraredWordCommand cmd = assembler.assemble(task);
        wordWriter.writeMeasureData(sensorId, cmd);
        bitWriter.writeMeasureCmdReq(sensorId, true);
        log.info("[HS] Infrared '{}' 發送指令 - 任務#{}", sensorId, task.getId());
    }

    private boolean hasCommandBeenSent(InfraredCommandStatus cmd, InfraredTask task) {
        return cmd != null && cmd.getInfraredNo() == task.getId().intValue();
    }

    private boolean isAckIssued(InfraredDeviceStatus status) {
        return status.isMeasureCmdIssued(); // 封裝：等同 measureCmdAck bit
    }

    private boolean isJobHandling(InfraredDeviceStatus status) {
        return Objects.equals(status.getInfraredStatus().getWorkingStatusText(), "Processing");
    }

    private boolean isCompletionRequested(InfraredDeviceStatus status) {
        return status.isMeasureCompReq();
    }

    private boolean isCompletionAcked(InfraredCommandStatus status) {
        return status != null && status.isMeasureCompAck();
    }

    private boolean shouldWriteDone(InfraredTask task) {
        return switch (task.getTaskStatus()) {
            case "COMPLETED", "FAILED", "CANCELLED", "SKIPPED" -> true;
            default -> false;
        };
    }

    private boolean isTerminalStatus(InfraredTask task) {
        return "COMPLETED".equals(task.getTaskStatus()) || "FAILED".equals(task.getTaskStatus());
    }

    private boolean markTaskAsDone(InfraredTask task) {
        return taskLifecycleService.markTaskAsDone(task);
    }

    private void resetCmdReq(Long id) {
        bitWriter.writeMeasureCmdReq(id, false);
    }

    private void setCompAck(Long id, boolean value) {
        bitWriter.writeMeasureCompAck(id, value);
    }

    private void resetCompAck(Long id) {
        setCompAck(id, false);
    }

    // 關鍵：僅在「Wait CMD」時允許送命令
    private boolean isWaitingForCommand(InfraredDeviceStatus status) {
        // 依你的定義：
        // s(DeviceStatus): 1=Idle, 2=Wait CMD, 3=Processing, 4=Complete
        // 必須 Standby & DeviceStatus=2 & 尚未 Ack（避免重複送）
        return status.isInfraredStandby()
                && status.getDeviceStatusCode() == 2
                && !status.isMeasureCmdAck()
                && !status.isAbnormal();
    }

    private String interpretReturnCode(int code) {
        return switch (code) {
            case 0x0100 -> "測量成功";
            case 0x0800 -> "命令中止";
            case 0x0F00 -> "測量失敗";
            default -> "未知 return code: 0x" + Integer.toHexString(code);
        };
    }
}