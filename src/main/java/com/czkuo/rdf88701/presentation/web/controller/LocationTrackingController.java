package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.query.LocationTrackingQuery;
import com.czkuo.rdf88701.application.dto.vo.LocationTrackingVO;
import com.czkuo.rdf88701.application.service.query.LocationTrackingQueryService;
import com.czkuo.rdf88701.common.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
