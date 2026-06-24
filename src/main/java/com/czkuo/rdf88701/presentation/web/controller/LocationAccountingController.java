package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.command.LocationAccountingCommand;
import com.czkuo.rdf88701.application.service.location.LocationAccountingService;
import com.czkuo.rdf88701.common.dto.ResponseResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LocationAccountingController
 * - 提供建帳、清帳、轉帳的外部控制接口
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@RestController
@RequestMapping("/api/location/accounting")
@RequiredArgsConstructor
public class LocationAccountingController {

    private final LocationAccountingService locationAccountingService;

    /**
     * 建帳（入帳）
     */
    @PostMapping("/entry")
    public ResponseResult<Void> entry(@RequestBody LocationAccountingCommand.EntryRequest request) {
        locationAccountingService.entry(
                request.getContainerMainId(),
                request.getLocationPointId(),
                request.getEntryType(),
                request.getOperator(),
                request.getSourceTaskId()
        );
        return ResponseResult.ok();
    }

    /**
     * 清帳（出帳）
     */
    @PostMapping("/exit")
    public ResponseResult<Void> exit(@RequestBody LocationAccountingCommand.ExitRequest request) {
        locationAccountingService.exit(
                request.getContainerMainId(),
                request.getExitType(),
                request.getOperator()
        );
        return ResponseResult.ok();
    }

    /**
     * 轉帳（出 + 入）
     */
    @PostMapping("/transfer")
    public ResponseResult<Void> transfer(@RequestBody LocationAccountingCommand.TransferRequest request) {
        locationAccountingService.transfer(
                request.getContainerMainId(),
                request.getToLocationPointId(),
                request.getEntryType(),
                request.getExitType(),
                request.getOperator(),
                request.getSourceTaskId()
        );
        return ResponseResult.ok();
    }
}
