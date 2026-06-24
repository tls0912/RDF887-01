package com.czkuo.rdf88701.domain.factory;

import com.czkuo.rdf88701.infra.entity.CraneRequest;
import com.czkuo.rdf88701.infra.entity.CraneTask;

import java.time.LocalDateTime;

/**
 * CraneTask Factory
 * - 由 CraneRequest 建立 CraneTask
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class CraneTaskFactory {

    /**
     * 將 crane_request 轉換為 crane_task
     *
     * @param request 原 crane_request
     * @param craneId 指定 crane 代碼
     */
    public static CraneTask createFromRequest(CraneRequest request, String craneId) {
        CraneTask task = new CraneTask();
        task.setRequestId(request.getId());
        task.setCraneId(craneId);
        task.setTaskType(request.getRequestType());
        task.setTaskStatus("PENDING");
        task.setPriorityLevel(0);
        task.setContainerMainId(request.getContainerMainId());
        task.setSourceLocationId(request.getSourceLocationId());
        task.setTargetLocationId(request.getTargetLocationId());
        task.setRemark(request.getRemark());

        LocalDateTime now = LocalDateTime.now();
        task.setCreatedTime(now);
        task.setUpdatedTime(now);

        return task;
    }
}
