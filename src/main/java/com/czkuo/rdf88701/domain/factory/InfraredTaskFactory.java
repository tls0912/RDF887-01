package com.czkuo.rdf88701.domain.factory;

import com.czkuo.rdf88701.infra.entity.InfraredRequest;
import com.czkuo.rdf88701.infra.entity.InfraredTask;

import java.time.LocalDateTime;

/**
 * InfraredTask Factory
 * - 由 InfraredRequest 建立 InfraredTask
 */
public class InfraredTaskFactory {

    /**
     * 將 infrared_request 轉換為 infrared_task
     *
     * @param request 原 infrared_request
     */
    public static InfraredTask createFromRequest(InfraredRequest request) {
        InfraredTask task = new InfraredTask();
        task.setRequestId(request.getId());
        task.setInfraredId(request.getInfraredId());
        task.setContainerMainId(request.getContainerMainId());
        task.setTaskType(request.getTaskType());
        task.setTaskStatus("PENDING");
        task.setPriorityLevel(0); // 初始預設優先等級
        task.setRemark(request.getRemark());

        LocalDateTime now = LocalDateTime.now();
        task.setCreatedTime(now);
        task.setUpdatedTime(now);

        return task;
    }
}
