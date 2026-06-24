package com.czkuo.rdf88701.domain.factory;

import com.czkuo.rdf88701.infra.entity.GripperRequest;
import com.czkuo.rdf88701.infra.entity.GripperTask;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * GripperTask Factory
 * - 由 GripperRequest 建立 GripperTask
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class GripperTaskFactory {

    /**
     * 將 gripper_request 轉換為 gripper_task
     *
     * @param request 原 gripper_request
     */
    public static GripperTask createFromRequest(GripperRequest request) {
        GripperTask task = new GripperTask();
        task.setRequestId(request.getId());
        task.setGripperId(request.getGripperId());
        task.setTaskType(request.getTaskType());
        task.setContainerMainId(request.getContainerMainId());
        task.setFromLocationId(request.getSourceLocationId());
        task.setToLocationId(request.getTargetLocationId());
        task.setTargetHeightMm(request.getTargetHeightMm());
        task.setLayerCount(request.getLayerCount());
        task.setTaskStatus("PENDING");
        task.setPriorityLevel(0); // 初始預設優先等級
        task.setRemark(request.getRemark());

        LocalDateTime now = LocalDateTime.now();
        task.setCreatedTime(now);
        task.setUpdatedTime(now);

        return task;
    }
}
