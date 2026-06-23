package com.czkuo.rdf88701.application.listener;

import com.czkuo.rdf88701.application.service.task.GripperTaskLifecycleService;
import com.czkuo.rdf88701.application.service.transfer.GripperTaskTransferService;
import com.czkuo.rdf88701.infra.entity.GripperTask;
import com.czkuo.rdf88701.infra.event.model.plc.gripper.GripperTaskCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Gripper 任務完成事件監聽器
 * - 根據 retCode 處理帳籍轉移與任務狀態更新
 * - 成功則更新 tracking，失敗則標記，其他則進行重試
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GripperTaskEventListener {

    private final GripperTaskLifecycleService gripperTaskLifecycleService;
    private final GripperTaskTransferService gripperTaskTransferService;

    @EventListener
    public void onGripperTaskCompleted(GripperTaskCompletedEvent event) {
        GripperTask task = event.getTask();
        int retCode = event.getRetCode();
        String description = event.getDescription();
        long taskId = task.getId();
        String taskType = task.getTaskType();

        log.info("[EVENT] Gripper 任務完成事件：任務#{} {} 完成 (code=0x{})({})",
                taskId, taskType, Integer.toHexString(retCode), description);

        switch (retCode) {
            case 0x100 -> {
                gripperTaskTransferService.updateFlowAndTrackingOnSuccess(task);
                gripperTaskTransferService.markTaskCompleted(task);
            }
            case 0x800 -> {
                gripperTaskTransferService.markTaskFailed(task, "任務中斷");
            }
            case 0xF00 -> {
                gripperTaskTransferService.markTaskFailed(task, "任務異常");
            }
            default -> {
                log.warn("[EVENT] Gripper 任務#{} 遇到未處理的回傳碼：0x{}", taskId, Integer.toHexString(retCode));
                gripperTaskLifecycleService.markRetry(task);
            }
        }
    }
}
