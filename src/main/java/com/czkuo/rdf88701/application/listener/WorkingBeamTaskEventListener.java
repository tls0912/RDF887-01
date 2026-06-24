package com.czkuo.rdf88701.application.listener;

import com.czkuo.rdf88701.application.service.task.WorkingBeamTaskLifecycleService;
import com.czkuo.rdf88701.application.service.transfer.WorkingBeamTaskTransferService;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;
import com.czkuo.rdf88701.infra.event.model.plc.workingbeam.WorkingBeamTaskCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * WorkingBeam 任務完成事件監聽器
 * - 負責根據 retCode 判斷是否要更新帳籍與狀態
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkingBeamTaskEventListener {

    private final WorkingBeamTaskLifecycleService workingBeamTaskLifecycleService;
    private final WorkingBeamTaskTransferService workingBeamTaskTransferService;

    @Async
    @EventListener
    public void onWorkingBeamTaskCompleted(WorkingBeamTaskCompletedEvent event) {
        WorkingBeamTask task = event.getTask();
        int retCode = event.getRetCode();
        String description = event.getDescription();
        long taskId = task.getId();

        log.info("[EVENT] WorkingBeam 任務完成事件：任務#{} 完成 (code=0x{})({})",
                taskId, Integer.toHexString(retCode), description);

        switch (retCode) {
            case 0x100 -> {
                workingBeamTaskTransferService.updateFlowAndTrackingOnSuccess(task);
                workingBeamTaskTransferService.markTaskCompleted(task);
            }
            case 0x800 -> {
                workingBeamTaskTransferService.markTaskFailed(task, "任務中斷");
            }
            case 0xF00 -> {
                workingBeamTaskTransferService.markTaskFailed(task, "任務異常");
            }
            default -> {
                log.warn("[EVENT] WorkingBeam 任務#{} 遇到未處理的回傳碼：0x{}", taskId, Integer.toHexString(retCode));
                workingBeamTaskLifecycleService.markRetry(task);
            }
        }
    }
}
