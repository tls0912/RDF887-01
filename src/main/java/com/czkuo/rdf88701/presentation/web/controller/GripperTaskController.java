package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.GripperTaskWithContainerDTO;
import com.czkuo.rdf88701.application.dto.query.GripperTaskWithContainerQuery;
import com.czkuo.rdf88701.application.service.query.GripperTaskQueryService;
import com.czkuo.rdf88701.common.dto.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@RestController
@RequestMapping("/api/gripper-task")
public class GripperTaskController {

    private final GripperTaskQueryService gripperTaskQueryService;

    public GripperTaskController(GripperTaskQueryService gripperTaskQueryService) {
        this.gripperTaskQueryService = gripperTaskQueryService;
    }

    /**
     * 分頁查詢 Gripper 任務（含容器資訊）
     *
     * @param query 查詢條件（gripperId, taskStatus, createdAfter, createdBefore）
     * @return 分頁資料（PageResult 包裝）
     */
    @GetMapping("/with-container")
    public PageResult<GripperTaskWithContainerDTO> queryWithContainer(GripperTaskWithContainerQuery query) {
        return gripperTaskQueryService.queryWithContainer(query);
    }
}
