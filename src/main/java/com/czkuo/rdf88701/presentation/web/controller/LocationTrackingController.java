package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.query.LocationTrackingQuery;
import com.czkuo.rdf88701.application.dto.vo.LocationTrackingVO;
import com.czkuo.rdf88701.application.service.query.LocationTrackingQueryService;
import com.czkuo.rdf88701.common.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * 容器位置追蹤查詢 Controller。
 *
 * <p>提供 `/api/location-trackings/page` 分頁查詢入口，用於檢視容器目前對應的
 * location_tracking 資料。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@RestController
@RequestMapping("/api/location-trackings")
@RequiredArgsConstructor
public class LocationTrackingController {

    private final LocationTrackingQueryService locationTrackingQueryService;

    @GetMapping("/page")
    public PageResult<LocationTrackingVO> queryPage(LocationTrackingQuery query) {
        return locationTrackingQueryService.queryPage(query);
    }
}
