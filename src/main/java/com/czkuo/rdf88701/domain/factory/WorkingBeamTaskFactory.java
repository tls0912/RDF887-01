package com.czkuo.rdf88701.domain.factory;

import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;

import java.time.LocalDateTime;

/**
 * WorkingBeamTask Factory
 * - 由 WorkingBeamRequest 建立 WorkingBeamTask
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class WorkingBeamTaskFactory {

    /**
     * 將 working_beam_request 轉換為 working_beam_task
     *
     * @param request 原 working_beam_request
     */
    public static WorkingBeamTask createFromRequest(WorkingBeamRequest request) {
        WorkingBeamTask task = new WorkingBeamTask();
        task.setRequestId(request.getId());
        task.setWorkingBeamId(String.valueOf(request.getWorkingBeamId()));
        task.setDirection(request.getDirection());
        task.setTaskStatus("PENDING");
        task.setPriorityLevel(0); // 初始預設優先等級
        task.setRemark(request.getRemark());

        LocalDateTime now = LocalDateTime.now();
        task.setCreatedTime(now);
        task.setUpdatedTime(now);

        return task;
    }
}
