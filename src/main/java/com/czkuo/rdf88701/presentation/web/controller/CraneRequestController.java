package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.command.CraneRequestCreateCommand;
import com.czkuo.rdf88701.application.dto.query.CraneRequestQuery;
import com.czkuo.rdf88701.application.dto.vo.CraneRequestVO;
import com.czkuo.rdf88701.application.service.command.CraneRequestCommandService;
import com.czkuo.rdf88701.application.service.query.CraneRequestQueryService;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.common.dto.ResponseResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@RestController
@RequestMapping("/api/crane-requests")
@RequiredArgsConstructor
public class CraneRequestController {

    private final CraneRequestQueryService craneRequestQueryService;
    private final CraneRequestCommandService craneRequestCommandService;

    /**
     * 查詢 Crane Request 分頁清單
     */
    @GetMapping("/page")
    public PageResult<CraneRequestVO> queryPage(CraneRequestQuery query) {
        return craneRequestQueryService.queryPage(query);
    }

    /**
     * 建立一筆 Crane Request
     */
    @PostMapping
    public ResponseResult<Long> create(@RequestBody CraneRequestCreateCommand command) {
        Long id = craneRequestCommandService.create(command);
        return ResponseResult.ok(id);
    }
}
