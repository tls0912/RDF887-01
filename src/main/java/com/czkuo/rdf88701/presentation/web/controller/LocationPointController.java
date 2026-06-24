package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.query.LocationPointQuery;
import com.czkuo.rdf88701.application.dto.vo.LocationPointVO;
import com.czkuo.rdf88701.application.service.query.LocationPointQueryService;
import com.czkuo.rdf88701.common.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * 庫位主檔查詢 Controller。
 *
 * <p>提供 `/api/location-points/page` 分頁查詢入口，回傳前端庫位畫面使用的
 * LocationPointVO。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@RestController
@RequestMapping("/api/location-points")
@RequiredArgsConstructor
public class LocationPointController {

    private final LocationPointQueryService locationPointQueryService;

    /**
     * 查詢 LocationPoint 分頁清單
     */
    @GetMapping("/page")
    public PageResult<LocationPointVO> queryPage(LocationPointQuery query) {
        return locationPointQueryService.queryPage(query);
    }
}

