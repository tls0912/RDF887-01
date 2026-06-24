package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.command.ExternalCraneRequestCreateCommand;
import com.czkuo.rdf88701.application.service.command.ExternalCraneRequestCommandService;
import com.czkuo.rdf88701.common.dto.ResponseResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * External 系統專用：Crane Request 建立接口
 * <p>
 * 典型場景：AMR / ASE / MES 外部系統發送 Crane 任務 Request
 * <p>
 * 注意：此接口不對 internal UI 或 operator 開放，專供外部 system 整合使用。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@RestController
@RequestMapping("/api/external/crane-requests")
@RequiredArgsConstructor
public class ExternalCraneRequestController {

    private final ExternalCraneRequestCommandService externalCraneRequestCommandService;

    /**
     * 外部系統發送建立 Crane Request
     * <p>
     * 外部只需要提供：containerMainCode + sourceLocationName + targetLocationName，
     * 系統自動轉換為內部 containerId + locationId。
     *
     * @param command 外部 Request Payload
     * @return 新建立的 CraneRequest ID
     */
    @PostMapping
    public ResponseResult<Long> create(@RequestBody ExternalCraneRequestCreateCommand command) {
        Long id = externalCraneRequestCommandService.create(command);
        return ResponseResult.ok(id);
    }
}
