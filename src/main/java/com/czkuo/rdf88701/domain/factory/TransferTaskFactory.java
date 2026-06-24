package com.czkuo.rdf88701.domain.factory;

import com.czkuo.rdf88701.infra.entity.TransferRequest;
import com.czkuo.rdf88701.infra.entity.TransferTask;

import java.time.LocalDateTime;

/**
 * TransferTask Factory
 * - 由 TransferRequest 建立 TransferTask
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class TransferTaskFactory {

    /**
     * 將 transfer_request 轉換為 transfer_task
     *
     * @param request 原 transfer_request
     */
    public static TransferTask createFromRequest(TransferRequest request) {
        TransferTask task = new TransferTask();
        task.setRequestId(request.getId());
        task.setTransferId(request.getTransferId());
        task.setTaskType(request.getTaskType());
        task.setContainerMainId(request.getContainerMainId());
        task.setFromLocationId(request.getSourceLocationId());
        task.setToLocationId(request.getTargetLocationId());
        task.setTaskStatus("PENDING");
        task.setPriorityLevel(0); // 初始預設優先等級
        task.setRemark(request.getRemark());

        LocalDateTime now = LocalDateTime.now();
        task.setCreatedTime(now);
        task.setUpdatedTime(now);

        return task;
    }
}

