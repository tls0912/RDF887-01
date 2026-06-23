package com.czkuo.rdf88701.application.listener;

import com.czkuo.rdf88701.application.service.task.TransferTaskLifecycleService;
import com.czkuo.rdf88701.application.service.transfer.TransferTaskTransferService;
import com.czkuo.rdf88701.infra.entity.TransferTask;
import com.czkuo.rdf88701.infra.event.model.plc.transfer.TransferTaskCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Transfer 任務完成事件監聽器
 * - 根據 retCode 決定後續行為（帳籍轉移、狀態更新）
 * - 特別處理 PICK（移到 Transfer）與 DROP（從 Transfer 移出）邏輯
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferTaskEventListener {

    private final TransferTaskLifecycleService transferTaskLifecycleService;
    private final TransferTaskTransferService transferTaskTransferService;

    @EventListener
    public void onTransferTaskCompleted(TransferTaskCompletedEvent event) {
        TransferTask task = event.getTask();
        int retCode = event.getRetCode();
        String description = event.getDescription();
        long taskId = task.getId();
        String taskType = task.getTaskType();

        log.info("[EVENT] Transfer 任務完成事件：任務#{} {} 完成 (code=0x{})({})",
                taskId, taskType, Integer.toHexString(retCode), description);

        switch (retCode) {
            case 0x100 -> {
                transferTaskTransferService.updateFlowAndTrackingOnSuccess(task);
                transferTaskTransferService.markTaskCompleted(task);
            }
            case 0x800 -> {
                transferTaskTransferService.markTaskFailed(task, "任務中斷");
            }
            case 0xF00 -> {
                transferTaskTransferService.markTaskFailed(task, "任務異常");
            }
            default -> {
                log.warn("[EVENT] Transfer 任務#{} 遇到未處理的回傳碼：0x{}", taskId, Integer.toHexString(retCode));
                transferTaskLifecycleService.markRetry(task);
            }
        }
    }
}
