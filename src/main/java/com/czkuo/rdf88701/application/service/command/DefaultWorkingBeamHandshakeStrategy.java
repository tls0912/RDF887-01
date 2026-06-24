package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.application.assembler.WorkingBeamWordCommandAssembler;
import com.czkuo.rdf88701.application.service.task.WorkingBeamTaskLifecycleService;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.*;
import com.czkuo.rdf88701.domain.repository.WorkingBeamHandshakeContextRepository;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcWorkingBeamWordCommand;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcWorkingBeamBitWriter;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcWorkingBeamWordWriter;
import com.czkuo.rdf88701.infra.cache.SiteStatusCache;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;
import com.czkuo.rdf88701.infra.event.model.plc.workingbeam.WorkingBeamTaskCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 預設 WorkingBeam 握手策略
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultWorkingBeamHandshakeStrategy implements WorkingBeamHandshakeStrategy {

    private final ApplicationEventPublisher eventPublisher;
    private final WorkingBeamWordCommandAssembler assembler;
    private final PlcWorkingBeamWordWriter wordWriter;
    private final PlcWorkingBeamBitWriter bitWriter;
    private final WorkingBeamHandshakeContextRepository contextRepository;
    private final WorkingBeamTaskLifecycleService taskLifecycleService;

    private final SiteStatusCache siteStatusCache;

    @Override
    public void tick(WorkingBeamTask task, WorkingBeamDeviceStatus status, WorkingBeamCommandStatus commandStatus) {
        int beamId = Integer.parseInt(task.getWorkingBeamId());
        Long taskId = task.getId();
        String beamName = "WB#" + beamId;

        WorkingBeamHandshakeContext ctx = contextRepository.getOrInit(taskId, beamName);
        WorkingBeamHandshakePhase phase = ctx.getPhase();

        // 每輪 tick 維持 TransferReady ON（可視實際需要放開）
        sendReady(task);

        switch (phase) {

            case NONE -> {
                // === 補償處理（順序很重要） ===

                // 若 PLC 已發出 Ack，表示任務已接收
                if (isAckIssued(status)) {
                    ctx.moveTo(WorkingBeamHandshakePhase.ACK_RECEIVED);
                }
                // 若已發出 CompletionAck，補償續接
                else if (isCompletionAcked(commandStatus)) {
                    ctx.moveTo(WorkingBeamHandshakePhase.RESPONDED_COMPLETION);
                }
                // 若目前為執行中（Processing）
                else if (isJobHandling(status)) {
                    if (hasCommandBeenSent(commandStatus, task)) {
                        // 任務 ID 一致 → 補償續接本任務
                        if (isTerminalStatus(task)) {
                            log.warn("[HS] 補償進入 RESPONDED_COMPLETION（JobHandling=true & ID 一致）");
                            ctx.moveTo(WorkingBeamHandshakePhase.RESPONDED_COMPLETION);
                        } else {
                            log.warn("[HS] 補償進入 IN_PROGRESS（JobHandling=true & ID 一致）");
                            ctx.moveTo(WorkingBeamHandshakePhase.IN_PROGRESS);
                            taskLifecycleService.markInProgress(task);
                        }
                    } else {
                        // 任務 ID 不一致 → 等待舊任務 CompletionReq 出現後補償
                        if (isCompletionRequested(status)) {
                            int transferNo = commandStatus != null ? commandStatus.getTransferNo() : -1;
                            int retCode = status.getReturnCodeValue();
                            String desc = interpretReturnCode(retCode);

                            log.info("[HS] 補償中：WorkingBeam '{}' 舊任務#{} CompletionReq 收到，回傳碼 0x{} ({}) → 回送 CompAck 並結束舊任務",
                                    beamId, transferNo, Integer.toHexString(retCode), desc);

                            setCompAck(beamId, true);
                            ctx.moveTo(WorkingBeamHandshakePhase.RESPONDED_COMPLETION);
                            return;
                        }

                        //log.debug("[HS] 補償等待中：JobHandling=true, TransferNo 不一致，尚未 CompletionReq");
                        return;
                    }
                }
                // 若尚未送出但已收到 CompletionReq → 直接接手處理
                else if (isCompletionRequested(status)) {
                    ctx.moveTo(WorkingBeamHandshakePhase.COMPLETION_RECEIVED);
                }
                // 任務在 DB 已標記完成 → 接手補發 CompletionAck
                else if (isTerminalStatus(task)) {
                    ctx.moveTo(WorkingBeamHandshakePhase.RESPONDED_COMPLETION);
                }
                // === 發送新指令 ===
                else {
                    sendCommand(task);
                    ctx.moveTo(WorkingBeamHandshakePhase.CMD_SENT);
                    taskLifecycleService.markDispatched(task);
                }
            }

            case CMD_SENT -> {
                if (isAckIssued(status)) {
                    ctx.moveTo(WorkingBeamHandshakePhase.ACK_RECEIVED);
                } else if (ctx.isTimeout(30)) {
                    log.warn("[HS] WorkingBeam '{}' 任務#{} 指令超時重送", beamId, taskId);
                    resetCmdReq(beamId);
                    taskLifecycleService.markRetry(task);
                    ctx.resetTimeout();
                    ctx.moveTo(WorkingBeamHandshakePhase.NONE);
                }
            }

            case ACK_RECEIVED -> {
                resetCmdReq(beamId);
                ctx.moveTo(WorkingBeamHandshakePhase.CMD_REQ_CLEARED);
            }

            case CMD_REQ_CLEARED -> {
                ctx.moveTo(WorkingBeamHandshakePhase.IN_PROGRESS);
                taskLifecycleService.markInProgress(task);
            }

            case IN_PROGRESS -> {
                if (isCompletionRequested(status)) {
                    ctx.moveTo(WorkingBeamHandshakePhase.COMPLETION_RECEIVED);
                } else if (ctx.isTimeout(30)) {
                    log.warn("[HS] WorkingBeam '{}' 任務#{} 執行超時補償", beamId, taskId);
                    resetCmdReq(beamId);
                    resetCompAck(beamId);
                    ctx.resetTimeout();
                    ctx.moveTo(WorkingBeamHandshakePhase.NONE);
                }
            }

            case COMPLETION_RECEIVED -> {
                int retCode = status.getReturnCode();
                String desc = interpretReturnCode(retCode);

                log.info("[HS] WorkingBeam '{}' 任務#{} 回傳碼：0x{} - {}", beamId, taskId,
                        Integer.toHexString(retCode), desc);

                eventPublisher.publishEvent(new WorkingBeamTaskCompletedEvent(this, task, retCode, desc));

                setCompAck(beamId, true);
                ctx.moveTo(WorkingBeamHandshakePhase.RESPONDED_COMPLETION);
            }

            case RESPONDED_COMPLETION -> {
                if (!isCompletionRequested(status)) {
                    setCompAck(beamId, false);
                    ctx.moveTo(WorkingBeamHandshakePhase.COMPLETION_CONFIRMED);
                }
            }

            case COMPLETION_CONFIRMED -> {
                // 僅在任務終態時才補寫 done_time，避免 RETRY 被壓成 done
                if (shouldWriteDone(task)) {
                    markTaskAsDone(task);
                    ctx.moveTo(WorkingBeamHandshakePhase.DONE);
                } else {
                    log.info("[HS] {} 任務#{} 非終態({})，不寫 done_time，回到 NONE 等待後續",
                            beamName, taskId, task.getTaskStatus());
                    resetCompAck(beamId); // 保險
                    ctx.moveTo(WorkingBeamHandshakePhase.NONE);
                }
            }

            case FAILED -> taskLifecycleService.markFailed(task);
            case DONE -> {}
        }

        contextRepository.save(ctx);
    }

    // ====== 內部邏輯工具方法 ======

    private void sendReady(WorkingBeamTask task) {
        int workingBeam = Integer.parseInt(task.getWorkingBeamId());
        bitWriter.writeTransferReady(workingBeam, true);
    }

    private void sendCommand(WorkingBeamTask task) {
        int workingBeam = Integer.parseInt(task.getWorkingBeamId());
        PlcWorkingBeamWordCommand cmd = assembler.assemble(task);
        wordWriter.writeTransferData(workingBeam, cmd);
        bitWriter.writeTransferCmdReq(workingBeam, true);
        log.info("[HS] WorkingBeam '{}' 發送指令 - 任務#{}", workingBeam, task.getId());
    }

    private boolean hasCommandBeenSent(WorkingBeamCommandStatus cmd, WorkingBeamTask task) {
        return cmd != null && cmd.getTransferNo() == task.getId().intValue();
    }

    private boolean isReadyToReceiveCommand(WorkingBeamDeviceStatus status) {
        return status.isTransferStandby();
    }

    private boolean isAckIssued(WorkingBeamDeviceStatus status) {
        return status.isTransferCmdIssued();
    }

    private boolean isJobHandling(WorkingBeamDeviceStatus status) {
        return Objects.equals(status.getWorkingBeamStatus().getWorkingStatusText(), "Processing");
    }

    private boolean isCompletionRequested(WorkingBeamDeviceStatus status) {
        return status.isTransferCompReq();
    }

    private boolean isCompletionAcked(WorkingBeamCommandStatus status) {
        return status != null && status.isTransferCompAck();
    }

    private boolean shouldWriteDone(WorkingBeamTask task) {
        return switch (task.getTaskStatus()) {
            case "COMPLETED", "FAILED", "CANCELLED", "SKIPPED" -> true;
            default -> false;
        };
    }

    private boolean isTerminalStatus(WorkingBeamTask task) {
        return "COMPLETED".equals(task.getTaskStatus()) || "FAILED".equals(task.getTaskStatus());
    }

    private boolean markTaskAsDone(WorkingBeamTask task) {
        return taskLifecycleService.markTaskAsDone(task);
    }

    private void resetCmdReq(int id) {
        bitWriter.writeTransferCmdReq(id, false);
    }

    private void setCompAck(int id, boolean value) {
        bitWriter.writeTransferCompAck(id, value);
    }

    private void resetCompAck(int id) {
        setCompAck(id, false);
    }

    private String interpretReturnCode(int code) {
        return switch (code) {
            case 0x01 -> "成功";
            case 0x02 -> "設備忙碌";
            case 0x03 -> "異常中止";
            default -> "未知 return code: 0x" + Integer.toHexString(code);
        };
    }
}
