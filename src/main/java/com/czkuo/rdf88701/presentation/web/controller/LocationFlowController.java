package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.query.LocationFlowQuery;
import com.czkuo.rdf88701.application.dto.vo.LocationFlowVO;
import com.czkuo.rdf88701.application.service.query.LocationFlowQueryService;
import com.czkuo.rdf88701.common.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LocationFlow 查詢 API
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@RestController
@RequestMapping("/api/location-flows")
@RequiredArgsConstructor
public class LocationFlowController {

    private final LocationFlowQueryService locationFlowQueryService;

    /**
     * 查詢 LocationFlow 分頁清單
     */
    @GetMapping("/page")
    public PageResult<LocationFlowVO> queryPage(LocationFlowQuery query) {
        return locationFlowQueryService.queryPage(query);
    }
}

