package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.application.assembler.CraneWordCommandAssembler;
import com.czkuo.rdf88701.application.service.task.CraneTaskLifecycleService;
import com.czkuo.rdf88701.common.exception.HandshakeException;
import com.czkuo.rdf88701.domain.plc.state.crane.*;
import com.czkuo.rdf88701.domain.repository.CraneHandshakeContextRepository;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcCraneWordCommand;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcCraneBitWriter;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcCraneWordWriter;
import com.czkuo.rdf88701.infra.entity.CraneTask;
import com.czkuo.rdf88701.infra.event.model.plc.crane.CraneTaskCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 預設 Crane 握手策略
 * - 根據上下文狀態逐步推進握手流程
 * - 從 FROM 起始，經過 TO 結束
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultCraneHandshakeStrategy implements CraneHandshakeStrategy {

    private final ApplicationEventPublisher eventPublisher;
    private final CraneWordCommandAssembler assembler;
    private final PlcCraneWordWriter wordWriter;
    private final PlcCraneBitWriter bitWriter;
    private final CraneHandshakeContextRepository contextRepository;
    private final CraneTaskLifecycleService craneTaskLifecycleService;

    @Override
    public void tick(CraneTask task, CraneDeviceStatus status, CraneCommandStatus commandStatus) {
        int craneId = Integer.parseInt(task.getCraneId());
        Long taskId = task.getId();
        String craneName = "Crane#" + craneId;

        CraneHandshakeContext ctx = contextRepository.getOrInit(taskId, craneName);

        boolean doFrom = shouldStartFrom(status, commandStatus);
        boolean doTo = shouldStartTo(status, commandStatus);

        if (!doFrom && !doTo) {
            if (hasCommandBeenSent(commandStatus, task, false)) {
                doTo = true;
            }
        }

        if (doFrom || doTo) {
            sendReady(task);
        }

        if (!doFrom && !doTo) {
            doFrom = status.isReadyHandleFromCmd();
            doTo = status.isReadyHandleToCmd();
        }

        if (doFrom) {
            tickSegment(task, status, commandStatus, true, ctx);
        } else if (doTo) {
            tickSegment(task, status, commandStatus, false, ctx);
        }

        contextRepository.save(ctx);
    }

    private void tickSegment(CraneTask task, CraneDeviceStatus status, CraneCommandStatus commandStatus,
                             boolean isFrom, CraneHandshakeContext ctx) {
        int craneId = Integer.parseInt(task.getCraneId());
        Long taskId = task.getId();
        CraneHandshakePhase phase = ctx.getPhase(isFrom);

        switch (phase) {
            case NONE -> {
                if (isAckIssued(status, isFrom)) {
                    ctx.moveTo(isFrom, CraneHandshakePhase.ACK_RECEIVED);
                } else if (isCompletionAcked(commandStatus, isFrom)) {
                    ctx.moveTo(isFrom, CraneHandshakePhase.RESPONDED_COMPLETION);
                } else if (isJobHandling(status, isFrom)) {
                    if (hasCommandBeenSent(commandStatus, task, isFrom)) {
                        // 任務編號一致，正常補償
                        if (isTerminalStatus(task)) {
                            log.warn("[HS] 補償進入 RESPONDED_COMPLETION（JobHandling=true & ID 一致）");
                            ctx.moveTo(isFrom, CraneHandshakePhase.RESPONDED_COMPLETION);
                        } else {
                            log.warn("[HS] 補償進入 IN_PROGRESS（JobHandling=true & ID 一致）");
                            ctx.moveTo(isFrom, CraneHandshakePhase.IN_PROGRESS);
                            craneTaskLifecycleService.markInProgress(task);
                        }
                    } else {
                        // 任務編號不一致 → 啟動回收流程
                        log.warn("[HS] 偵測異常 JobHandling，但 TransferNo 不符 → 進行殘留任務補償");

                        // 先等待 PLC CompletionReq，等它結束
                        if (isCompletionRequested(status, isFrom)) {
                            int transferNo = isFrom ? commandStatus.getFromTransferNo() : commandStatus.getToTransferNo();
                            int retCode = isFrom ? status.getFromReturnCodeValue() : status.getToReturnCodeValue();
                            String desc = isFrom ? interpretFromReturnCode(retCode) : interpretToReturnCode(retCode);

                            log.info("[HS] 補償中：Crane '{}' 任務#{} [{}] 收到 CompletionReq，回傳碼 0x{} ({}) → 回送 CompAck 並結束上次任務",
                                    craneId, transferNo, isFrom ? "FROM" : "TO", Integer.toHexString(retCode), desc);

                            setCompAck(craneId, isFrom, true);
                            ctx.moveTo(isFrom, CraneHandshakePhase.RESPONDED_COMPLETION);
                            return;
                        }

                        // 補償流程尚未結束 → 不可進入後續狀態
                        //log.debug("[HS] 補償等待中：JobHandling=true, TransferNo 不一致，但尚未 CompletionReq");
                    }
                } else if (isCompletionRequested(status, isFrom)) {
                    ctx.moveTo(isFrom, CraneHandshakePhase.COMPLETION_RECEIVED);
                } else if (isTerminalStatus(task)) {
                    ctx.moveTo(isFrom, CraneHandshakePhase.RESPONDED_COMPLETION);
                } else if (!isReadyHandleCmd(status, isFrom)) {
                    //log.debug("[HS] Crane '{}' 任務#{} 等待 PLC readyHandle{}Cmd", craneId, taskId, isFrom ? "From" : "To");
                } else {
                    sendCommand(task, isFrom);
                    ctx.moveTo(isFrom, CraneHandshakePhase.CMD_SENT);
                    craneTaskLifecycleService.markDispatched(task);
                }
            }
            case CMD_SENT -> {
                if (isAckIssued(status, isFrom)) {
                    ctx.moveTo(isFrom, CraneHandshakePhase.ACK_RECEIVED);
                } else if (ctx.isTimeout(isFrom, 30)) {
                    log.warn("[HS] Crane '{}' 任務#{} {} 指令超時重送", craneId, taskId, isFrom ? "FROM" : "TO");
                    resetCmdReq(craneId, isFrom);
                    ctx.resetTimeout(isFrom);
                    ctx.moveTo(isFrom, CraneHandshakePhase.NONE);
                }
            }
            case ACK_RECEIVED -> {
                resetCmdReq(craneId, isFrom);
                ctx.moveTo(isFrom, CraneHandshakePhase.CMD_REQ_CLEARED);
            }
            case CMD_REQ_CLEARED -> {
                if (isJobHandling(status, isFrom)) {
                    ctx.moveTo(isFrom, CraneHandshakePhase.IN_PROGRESS);
                    craneTaskLifecycleService.markInProgress(task);
                }
            }
            case IN_PROGRESS -> {
                if (isCompletionRequested(status, isFrom)) {
                    ctx.moveTo(isFrom, CraneHandshakePhase.COMPLETION_RECEIVED);
                } else if (ctx.isTimeout(isFrom, 300)) {
                    log.warn("[HS] Crane '{}' 任務#{} 執行超時補償 {}", craneId, taskId, isFrom ? "FROM" : "TO");
                    resetCmdReq(craneId, isFrom);
                    resetCompAck(craneId, isFrom);
                    ctx.resetTimeout(isFrom);
                    ctx.moveTo(isFrom, CraneHandshakePhase.NONE);
                }
            }
            case COMPLETION_RECEIVED -> {
                int retCode = isFrom ? status.getFromReturnCodeValue() : status.getToReturnCodeValue();
                String desc = isFrom ? interpretFromReturnCode(retCode) : interpretToReturnCode(retCode);

                // 取產品資訊
                String productId = status.getProductId();
                double productHeight = status.getProductHeight() / 100.0;

                // 加上產品 ID 與高度；回傳碼以 2 位大寫十六進位顯示
                log.info("[HS] Crane '{}' 任務#{} {} 回傳碼：{} - {} | ProductId='{}' Height={}",
                        craneId, taskId, (isFrom ? "FROM" : "TO"),
                        String.format("0x%02X", retCode), desc, productId, productHeight);

                if (isFrom && retCode == 1 && productHeight <= 0) {
                    break;
                }

                try {
                    handleBusinessAfterCompletion(task, productId, productHeight, isFrom, retCode, desc);
                } catch (HandshakeException ex) {
                    if (ex.isRetryable()) {
                        log.warn("[HS] Crane '{}' 任務#{} {} 業務處理失敗，稍後重試: {}", craneId, taskId, isFrom ? "FROM" : "TO", ex.getMessage());
                        return;
                    } else {
                        log.error("[HS] Crane '{}' 任務#{} {} 業務處理錯誤，任務失敗: {}", craneId, taskId, isFrom ? "FROM" : "TO", ex.getMessage());
                        ctx.markFailed(isFrom, ex.getMessage());
                        craneTaskLifecycleService.markFailed(task, ex.getMessage());
                        return;
                    }
                }

                setCompAck(craneId, isFrom, true);
                ctx.moveTo(isFrom, CraneHandshakePhase.RESPONDED_COMPLETION);
            }
            case RESPONDED_COMPLETION -> {
                if (!isCompletionRequested(status, isFrom)) {
                    setCompAck(craneId, isFrom, false);
                    ctx.moveTo(isFrom, CraneHandshakePhase.COMPLETION_CONFIRMED);
                }
            }
            case COMPLETION_CONFIRMED -> {
                // 僅在任務終態時才補寫 done_time，避免 RETRY 被壓成 done
                if (shouldWriteDone(task)) {
                    markTaskAsDone(task, isFrom);
                    ctx.moveTo(isFrom, CraneHandshakePhase.DONE);
                } else {
                    log.info("[HS] Crane#1 任務#{} 非終態({})，不寫 done_time，回到 NONE 等待後續",
                            taskId, task.getTaskStatus());
                    resetCompAck(craneId, isFrom); // 保險
                    ctx.moveTo(isFrom, CraneHandshakePhase.NONE);
                }
            }
            case FAILED -> craneTaskLifecycleService.markFailed(task); // 應該用不到
            case DONE -> {}
        }
    }

    private void handleBusinessAfterCompletion(CraneTask task, String productId, double productHeight, boolean isFrom, int retCode, String desc) {
        eventPublisher.publishEvent(new CraneTaskCompletedEvent(this, task, productId, productHeight, isFrom, retCode, desc));
    }

    private boolean shouldStartFrom(CraneDeviceStatus status, CraneCommandStatus craneCommandStatus) {
        return status.isFromJobHandling() || craneCommandStatus.isFromTransferCmdReq() || craneCommandStatus.isFromTransferCompAck();
    }

    private boolean shouldStartTo(CraneDeviceStatus status, CraneCommandStatus craneCommandStatus) {
        return status.isToJobHandling() || craneCommandStatus.isToTransferCmdReq() || craneCommandStatus.isToTransferCompAck();
    }

    private boolean isReadyHandleCmd(CraneDeviceStatus status, boolean isFrom) {
        return isFrom ? status.isReadyHandleFromCmd() : status.isReadyHandleToCmd();
    }

    private void sendReady(CraneTask task) {
        int crane = Integer.parseInt(task.getCraneId());
        bitWriter.writeTransferReady(crane, true);
    }

    private void sendCommand(CraneTask task, boolean isFrom) {
        int crane = Integer.parseInt(task.getCraneId());
        PlcCraneWordCommand cmd = isFrom ? assembler.assembleFromSection(task) : assembler.assembleToSection(task);
        if (isFrom) {
            wordWriter.writeFromTransferData(crane, cmd);
            bitWriter.writeFromTransferCmdReq(crane, true);
        } else {
            wordWriter.writeToTransferData(crane, cmd);
            bitWriter.writeToTransferCmdReq(crane, true);
        }
        log.info("[HS] Crane '{}' 發送 {} 指令 - 任務#{}", crane, isFrom ? "FROM" : "TO", task.getId());
    }

    private void triggerCmdReq(int craneId, boolean isFrom) {
        if (isFrom) bitWriter.writeFromTransferCmdReq(craneId, true);
        else bitWriter.writeToTransferCmdReq(craneId, true);
    }

    private boolean hasCmdReqBeenSet(CraneCommandStatus cmd, boolean isFrom) {
        if (cmd == null) return false;
        return isFrom ? cmd.isFromTransferCmdReq() : cmd.isToTransferCmdReq();
    }

    private boolean hasCompAckBeenSet(CraneCommandStatus cmd, boolean isFrom) {
        if (cmd == null) return false;
        return isFrom ? cmd.isFromTransferCompAck() : cmd.isToTransferCompAck();
    }

    private boolean hasCommandBeenSent(CraneCommandStatus cmd, CraneTask task, boolean isFrom) {
        if (cmd == null) return false;
        return isFrom ?
                cmd.getFromTransferNo() == task.getId().intValue() :
                cmd.getToTransferNo() == task.getId().intValue();
    }

    private void resetCmdReq(int craneId, boolean isFrom) {
        if (isFrom) bitWriter.writeFromTransferCmdReq(craneId, false);
        else bitWriter.writeToTransferCmdReq(craneId, false);
    }

    private void setCompAck(int craneId, boolean isFrom, boolean value) {
        if (isFrom) bitWriter.writeFromTransferCompAck(craneId, value);
        else bitWriter.writeToTransferCompAck(craneId, value);
    }

    private void resetCompAck(int craneId, boolean isFrom) {
        setCompAck(craneId, isFrom, false);
    }

    private boolean isAckIssued(CraneDeviceStatus status, boolean isFrom) {
        return isFrom ? status.isFromTransferCmdIssued() : status.isToTransferCmdIssued();
    }

    private boolean isJobHandling(CraneDeviceStatus status, boolean isFrom) {
        return isFrom ? status.isFromJobHandling() : status.isToJobHandling();
    }

    private boolean isCompletionRequested(CraneDeviceStatus status, boolean isFrom) {
        return isFrom ? status.isFromTransferCompReq() : status.isToTransferCompReq();
    }

    private boolean isCompletionAcked(CraneCommandStatus status, boolean isFrom) {
        return isFrom ? status.isFromTransferCompAck() : status.isToTransferCompAck();
    }

    private boolean markTaskAsDone(CraneTask task, boolean isFrom) {
        boolean mark = !isFrom || isTerminalStatus(task);
        return !mark || craneTaskLifecycleService.markTaskAsDone(task);
    }

    private boolean shouldWriteDone(CraneTask task) {
        return switch (task.getTaskStatus()) {
            case "COMPLETED", "FAILED", "CANCELLED", "SKIPPED" -> true;
            default -> false;
        };
    }

    private boolean isTerminalStatus(CraneTask task) {
        return switch (task.getTaskStatus()) {
            case "COMPLETED", "FAILED" -> true;
            default -> false;
        };
    }

    private String interpretFromReturnCode(int code) {
        return switch (code) {
            case 0x01 -> "成功";
            case 0x04 -> "夾取異常";
            case 0x06 -> "空取 - 無物";
            case 0x08 -> "命令中止";
            case 0x0E -> "空取完成";
            case 0x0F -> "未知錯誤";
            case 0x1D -> "重複儲存";
            default -> "未知 return code: 0x" + Integer.toHexString(code);
        };
    }

    private String interpretToReturnCode(int code) {
        return switch (code) {
            case 0x10 -> "成功";
            case 0x40 -> "夾放異常";
            case 0x60 -> "放置已有物";
            case 0x80 -> "命令中止";
            case 0xD0 -> "雙重儲存";
            case 0xF0 -> "未知錯誤";
            default -> "未知 return code: 0x" + Integer.toHexString(code);
        };
    }
}
