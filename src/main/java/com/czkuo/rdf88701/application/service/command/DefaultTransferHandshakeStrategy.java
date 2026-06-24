package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.application.assembler.TransferWordCommandAssembler;
import com.czkuo.rdf88701.application.service.task.TransferTaskLifecycleService;
import com.czkuo.rdf88701.domain.plc.state.transfer.*;
import com.czkuo.rdf88701.domain.repository.TransferHandshakeContextRepository;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcTransferWordCommand;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcTransferBitWriter;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcTransferWordWriter;
import com.czkuo.rdf88701.infra.entity.TransferTask;
import com.czkuo.rdf88701.infra.event.model.plc.transfer.TransferTaskCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 預設 Transfer 握手策略實作
 *
 * - 負責處理單一 Transfer 任務的握手流程邏輯（依狀態機逐步推進）
 * - 透過 PLC Bit/Word Writer 發送控制信號
 * - 快取握手上下文（TransferHandshakeContext）來追蹤當前階段與逾時控制
 * - 發送事件供系統進一步處理（如通知前端、寫入歷史）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultTransferHandshakeStrategy implements TransferHandshakeStrategy {

    private final ApplicationEventPublisher eventPublisher;
    private final TransferWordCommandAssembler assembler;
    private final PlcTransferWordWriter wordWriter;
    private final PlcTransferBitWriter bitWriter;
    private final TransferHandshakeContextRepository contextRepository;
    private final TransferTaskLifecycleService taskLifecycleService;

    /**
     * 執行握手狀態推進
     */
    @Override
    public void tick(TransferTask task, TransferDeviceStatus status, TransferCommandStatus commandStatus) {
        int transferId = Integer.parseInt(task.getTransferId().toString());
        Long taskId = task.getId();
        String transferName = "TR#" + transferId;

        TransferHandshakeContext ctx = contextRepository.getOrInit(taskId, transferName);
        TransferHandshakePhase phase = ctx.getPhase();

        sendReady(task);

        switch (phase) {

            case NONE -> {
                if (isAckIssued(status)) {
                    ctx.moveTo(TransferHandshakePhase.ACK_RECEIVED);
                } else if (isCompletionAcked(commandStatus)) {
                    ctx.moveTo(TransferHandshakePhase.RESPONDED_COMPLETION);
                } else if (isJobHandling(status)) {
                    if (hasCommandBeenSent(commandStatus, task)) {
                        // 任務編號一致，正常補償
                        if (isTerminalStatus(task)) {
                            log.warn("[HS] 補償進入 RESPONDED_COMPLETION（JobHandling=true & ID 一致）");
                            ctx.moveTo(TransferHandshakePhase.RESPONDED_COMPLETION);
                        } else {
                            log.warn("[HS] 補償進入 IN_PROGRESS（JobHandling=true & ID 一致）");
                            ctx.moveTo(TransferHandshakePhase.IN_PROGRESS);
                            taskLifecycleService.markInProgress(task);
                        }
                    } else {
                        // 任務編號不一致 → 啟動回收流程
                        log.warn("[HS] 偵測異常 JobHandling，但 TransferNo 不符 → 進行殘留任務補償");

                        // 先等待 PLC CompletionReq，等它結束
                        if (isCompletionRequested(status)) {
                            int transferNo = commandStatus.getTransferNo();
                            int retCode = status.getReturnCode();
                            String desc = interpretReturnCode(retCode);

                            log.info("[HS] 補償中：Transfer '{}' 任務#{} 收到 CompletionReq，回傳碼 0x{} ({}) → 回送 CompAck 並結束上次任務",
                                    transferId, transferNo, Integer.toHexString(retCode), desc);

                            setCompAck(transferId, true);
                            ctx.moveTo(TransferHandshakePhase.RESPONDED_COMPLETION);
                            return;
                        }

                        // 補償流程尚未結束 → 不可進入後續狀態
                        //log.debug("[HS] 補償等待中：JobHandling=true, TransferNo 不一致，但尚未 CompletionReq");
                        return;
                    }
                } else if (isCompletionRequested(status)) {
                    ctx.moveTo(TransferHandshakePhase.COMPLETION_RECEIVED);
                } else if (isTerminalStatus(task)) {
                    ctx.moveTo(TransferHandshakePhase.RESPONDED_COMPLETION);
                } else {
                    sendCommand(task);
                    ctx.moveTo(TransferHandshakePhase.CMD_SENT);
                    taskLifecycleService.markDispatched(task);
                }
            }
            case CMD_SENT -> {
                if (isAckIssued(status)) {
                    ctx.moveTo(TransferHandshakePhase.ACK_RECEIVED);
                } else if (ctx.isTimeout(30)) {
                    log.warn("[HS] Transfer '{}' 任務#{} 指令超時重送", transferName, taskId);
                    resetCmdReq(transferId);
                    taskLifecycleService.markRetry(task);
                    ctx.resetTimeout();
                    ctx.moveTo(TransferHandshakePhase.NONE); // 重進流程
                }
            }
            case ACK_RECEIVED -> {
                resetCmdReq(transferId);
                ctx.moveTo(TransferHandshakePhase.CMD_REQ_CLEARED);
                log.info("[HS] Transfer '{}' 任務#{} 清除 CMD", transferName, taskId);
            }
            case CMD_REQ_CLEARED -> {
                ctx.moveTo(TransferHandshakePhase.IN_PROGRESS);
                taskLifecycleService.markInProgress(task);
            }
            case IN_PROGRESS -> {
                if (isCompletionRequested(status)) {
                    ctx.moveTo(TransferHandshakePhase.COMPLETION_RECEIVED);
                } else if (ctx.isTimeout(300)) {
                    log.warn("[HS] Transfer '{}' 任務#{} 執行超時補償", transferName, taskId);
                    resetCmdReq(transferId);
                    resetCompAck(transferId);
                    ctx.resetTimeout();
                    ctx.moveTo(TransferHandshakePhase.NONE); // 重進流程
                }
            }
            case COMPLETION_RECEIVED -> {
                int retCode = status.getReturnCodeValue();
                String desc = interpretReturnCode(retCode);

                log.info("[HS] Transfer '{}' 任務#{} 回傳碼：0x{} - {}", transferName, taskId,
                        Integer.toHexString(retCode), desc);

                eventPublisher.publishEvent(new TransferTaskCompletedEvent(this, task, retCode, desc));

                setCompAck(transferId, true);
                ctx.moveTo(TransferHandshakePhase.RESPONDED_COMPLETION);
            }
            case RESPONDED_COMPLETION -> {
                if (!isCompletionRequested(status)) {
                    setCompAck(transferId, false);
                    ctx.moveTo(TransferHandshakePhase.COMPLETION_CONFIRMED);
                }
            }
            case COMPLETION_CONFIRMED -> {
                // 僅在任務終態時才補寫 done_time，避免 RETRY 被壓成 done
                if (shouldWriteDone(task)) {
                    markTaskAsDone(task);
                    ctx.moveTo(TransferHandshakePhase.DONE);
                } else {
                    log.info("[HS] {} 任務#{} 非終態({})，不寫 done_time，回到 NONE 等待後續",
                            transferName, taskId, task.getTaskStatus());
                    resetCompAck(transferId); // 保險
                    ctx.moveTo(TransferHandshakePhase.NONE);
                }
            }
            case FAILED -> taskLifecycleService.markFailed(task);
            case DONE -> {}
        }

        contextRepository.save(ctx);
    }

    // ========= Control / Status Methods =========

    private void sendReady(TransferTask task) {
        int transfer = task.getTransferId().intValue();
        bitWriter.writeTransferReady(transfer, true);
    }

    private void sendCommand(TransferTask task) {
        int transferId = Integer.parseInt(task.getTransferId().toString());
        PlcTransferWordCommand cmd = assembler.assemble(task);
        wordWriter.writeTransferData(transferId, cmd);
        bitWriter.writeTransferCmdReq(transferId, true);
        log.info("[HS] Transfer '{}' 發送指令 - 任務#{}", transferId, task.getId());
    }

    private boolean hasCommandBeenSent(TransferCommandStatus cmd, TransferTask task) {
        if (cmd == null) return false;
        return cmd.getTransferNo() == task.getId().intValue();
    }

    private void resetCmdReq(int transferId) {
        bitWriter.writeTransferCmdReq(transferId, false);
    }

    private void setCompAck(int transferId, boolean value) {
        bitWriter.writeTransferCompAck(transferId, value);
    }

    private void resetCompAck(int transferId) {
        setCompAck(transferId, false);
    }

    private boolean isReadyToReceiveCommand(TransferDeviceStatus status) {
        return status.isTransferStandby();
    }

    private boolean isAckIssued(TransferDeviceStatus status) {
        return status.isTransferCmdIssued();
    }

    private boolean isJobHandling(TransferDeviceStatus status) {
        return Objects.equals(status.getTransferStatus().getWorkingStatusText(), "Processing");
    }

    private boolean isCompletionRequested(TransferDeviceStatus status) {
        return status.isTransferCompReq();
    }

    private boolean isCompletionAcked(TransferCommandStatus status) {
        if (status == null) return false;
        return status.isTransferCompAck();
    }

    private boolean markTaskAsDone(TransferTask task) {
        return taskLifecycleService.markTaskAsDone(task);
    }

    private boolean shouldWriteDone(TransferTask task) {
        return switch (task.getTaskStatus()) {
            case "COMPLETED", "FAILED", "CANCELLED", "SKIPPED" -> true;
            default -> false;
        };
    }

    private boolean isTerminalStatus(TransferTask task) {
        return switch (task.getTaskStatus()) {
            case "COMPLETED", "FAILED" -> true;
            default -> false;
        };
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
