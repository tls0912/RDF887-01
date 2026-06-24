package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.application.assembler.GripperWordCommandAssembler;
import com.czkuo.rdf88701.application.service.task.GripperTaskLifecycleService;
import com.czkuo.rdf88701.domain.plc.state.gripper.*;
import com.czkuo.rdf88701.domain.repository.GripperHandshakeContextRepository;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcGripperWordCommand;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcGripperBitWriter;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcGripperWordWriter;
import com.czkuo.rdf88701.infra.entity.GripperTask;
import com.czkuo.rdf88701.infra.event.model.plc.gripper.GripperTaskCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 預設 Gripper 握手策略實作
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGripperHandshakeStrategy implements GripperHandshakeStrategy {

    private final ApplicationEventPublisher eventPublisher;
    private final GripperWordCommandAssembler assembler;
    private final PlcGripperWordWriter wordWriter;
    private final PlcGripperBitWriter bitWriter;
    private final GripperHandshakeContextRepository contextRepository;
    private final GripperTaskLifecycleService taskLifecycleService;

    @Override
    public void tick(GripperTask task, GripperDeviceStatus status, GripperCommandStatus commandStatus) {
        int gripperId = task.getGripperId().intValue();
        Long taskId = task.getId();
        String gripperName = "GP#" + gripperId;

        GripperHandshakeContext ctx = contextRepository.getOrInit(taskId, gripperName);
        GripperHandshakePhase phase = ctx.getPhase();

        sendReady(task);

        switch (phase) {
            case NONE -> {
                if (isAckIssued(status)) {
                    ctx.moveTo(GripperHandshakePhase.ACK_RECEIVED);
                } else if (isCompletionAcked(commandStatus)) {
                    ctx.moveTo(GripperHandshakePhase.RESPONDED_COMPLETION);
                } else if (isJobHandling(status)) {
                    if (hasCommandBeenSent(commandStatus, task)) {
                        if (isTerminalStatus(task)) {
                            ctx.moveTo(GripperHandshakePhase.RESPONDED_COMPLETION);
                        } else {
                            ctx.moveTo(GripperHandshakePhase.IN_PROGRESS);
                            taskLifecycleService.markInProgress(task);
                        }
                    } else {
                        if (isCompletionRequested(status)) {
                            int gripperNo = commandStatus.getTransferNo();
                            int retCode = status.getReturnCode();
                            String desc = interpretReturnCode(retCode);

                            log.info("[HS] Gripper '{}' 任務#{} 收到 CompletionReq，回傳碼 0x{} ({}) → 回送 CompAck 並結束上次任務",
                                    gripperId, gripperNo, Integer.toHexString(retCode), desc);

                            setCompAck(gripperId, true);
                            ctx.moveTo(GripperHandshakePhase.RESPONDED_COMPLETION);
                            return;
                        }
                        return;
                    }
                } else if (isCompletionRequested(status)) {
                    ctx.moveTo(GripperHandshakePhase.COMPLETION_RECEIVED);
                } else if (isTerminalStatus(task)) {
                    ctx.moveTo(GripperHandshakePhase.RESPONDED_COMPLETION);
                } else {
                    sendCommand(task);
                    ctx.moveTo(GripperHandshakePhase.CMD_SENT);
                    taskLifecycleService.markDispatched(task);
                }
            }
            case CMD_SENT -> {
                if (isAckIssued(status)) {
                    ctx.moveTo(GripperHandshakePhase.ACK_RECEIVED);
                } else if (ctx.isTimeout(30)) {
                    log.warn("[HS] Gripper '{}' 任務#{} 指令超時重送", gripperName, taskId);
                    resetCmdReq(gripperId);
                    taskLifecycleService.markRetry(task);
                    ctx.resetTimeout();
                    ctx.moveTo(GripperHandshakePhase.NONE);
                }
            }
            case ACK_RECEIVED -> {
                resetCmdReq(gripperId);
                ctx.moveTo(GripperHandshakePhase.CMD_REQ_CLEARED);
                log.info("[HS] Gripper '{}' 任務#{} 清除 CMD", gripperName, taskId);
            }
            case CMD_REQ_CLEARED -> {
                ctx.moveTo(GripperHandshakePhase.IN_PROGRESS);
                taskLifecycleService.markInProgress(task);
            }
            case IN_PROGRESS -> {
                if (isCompletionRequested(status)) {
                    ctx.moveTo(GripperHandshakePhase.COMPLETION_RECEIVED);
                } else if (ctx.isTimeout(180)) {
                    log.warn("[HS] Gripper '{}' 任務#{} 執行超時補償", gripperName, taskId);
                    resetCmdReq(gripperId);
                    resetCompAck(gripperId);
                    ctx.resetTimeout();
                    ctx.moveTo(GripperHandshakePhase.NONE);
                }
            }
            case COMPLETION_RECEIVED -> {
                int retCode = status.getReturnCode();
                String desc = interpretReturnCode(retCode);

                log.info("[HS] Gripper '{}' 任務#{} 回傳碼：0x{} - {}", gripperName, taskId,
                        Integer.toHexString(retCode), desc);

                eventPublisher.publishEvent(new GripperTaskCompletedEvent(this, task, retCode, desc));

                setCompAck(gripperId, true);
                ctx.moveTo(GripperHandshakePhase.RESPONDED_COMPLETION);
            }
            case RESPONDED_COMPLETION -> {
                if (!isCompletionRequested(status)) {
                    setCompAck(gripperId, false);
                    ctx.moveTo(GripperHandshakePhase.COMPLETION_CONFIRMED);
                }
            }
            case COMPLETION_CONFIRMED -> {
                // 僅在任務終態時才補寫 done_time，避免 RETRY 被壓成 done
                if (shouldWriteDone(task)) {
                    markTaskAsDone(task);
                    ctx.moveTo(GripperHandshakePhase.DONE);
                } else {
                    log.info("[HS] {} 任務#{} 非終態({})，不寫 done_time，回到 NONE 等待後續",
                            gripperName, taskId, task.getTaskStatus());
                    resetCompAck(gripperId); // 保險
                    ctx.moveTo(GripperHandshakePhase.NONE);
                }
            }
            case FAILED -> taskLifecycleService.markFailed(task);
            case DONE -> {
            }
        }

        contextRepository.save(ctx);
    }

    // ========= Control / Status Methods =========

    private void sendReady(GripperTask task) {
        int gripper = task.getGripperId().intValue();
        bitWriter.writeGripperReady(gripper, true);
    }

    private void sendCommand(GripperTask task) {
        int gripperId = task.getGripperId().intValue();
        PlcGripperWordCommand cmd = assembler.assemble(task);
        wordWriter.writeGripperData(gripperId, cmd);
        bitWriter.writeGripperCmdReq(gripperId, true);
        log.info("[HS] Gripper '{}' 發送指令 - 任務#{}", gripperId, task.getId());
    }

    private boolean hasCommandBeenSent(GripperCommandStatus cmd, GripperTask task) {
        if (cmd == null) return false;
        return cmd.getTransferNo() == task.getId().intValue();
    }

    private void resetCmdReq(int gripperId) {
        bitWriter.writeGripperCmdReq(gripperId, false);
    }

    private void setCompAck(int gripperId, boolean value) {
        bitWriter.writeGripperCompAck(gripperId, value);
    }

    private void resetCompAck(int gripperId) {
        setCompAck(gripperId, false);
    }

    private boolean isAckIssued(GripperDeviceStatus status) {
        return status.isGripperCmdIssued();
    }

    private boolean isJobHandling(GripperDeviceStatus status) {
        return Objects.equals(status.getGripperStatus().getWorkingStatusText(), "Processing");
    }

    private boolean isCompletionRequested(GripperDeviceStatus status) {
        return status.isTransferCompReq();
    }

    private boolean isCompletionAcked(GripperCommandStatus status) {
        if (status == null) return false;
        return status.isTransferCompAck();
    }

    private boolean markTaskAsDone(GripperTask task) {
        return taskLifecycleService.markTaskAsDone(task);
    }

    private boolean shouldWriteDone(GripperTask task) {
        return switch (task.getTaskStatus()) {
            case "COMPLETED", "FAILED", "CANCELLED", "SKIPPED" -> true;
            default -> false;
        };
    }

    private boolean isTerminalStatus(GripperTask task) {
        return switch (task.getTaskStatus()) {
            case "COMPLETED", "FAILED" -> true;
            default -> false;
        };
    }

    private String interpretReturnCode(int code) {
        return switch (code) {
            case 0x100 -> "成功";
            case 0x02 -> "設備忙碌";
            case 0x03 -> "異常中止";
            default -> "未知 return code: 0x" + Integer.toHexString(code);
        };
    }
}
