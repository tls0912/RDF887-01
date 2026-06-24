package com.czkuo.rdf88701.application.service.query;

import com.czkuo.rdf88701.application.dto.query.LocationTrackingQuery;
import com.czkuo.rdf88701.application.dto.vo.LocationTrackingVO;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.infra.entity.LocationTracking;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LocationTracking 查詢服務
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
public class LocationTrackingQueryService {

    private final LocationTrackingRepository locationTrackingRepository;

    public LocationTrackingQueryService(LocationTrackingRepository locationTrackingRepository) {
        this.locationTrackingRepository = locationTrackingRepository;
    }

    /**
     * 根據查詢條件查詢 LocationTracking 分頁資料
     *
     * @param query 查詢條件
     * @return 分頁結果
     */
    public PageResult<LocationTrackingVO> queryPage(LocationTrackingQuery query) {
        PageResult<LocationTracking> rawPage = locationTrackingRepository.findPageByCondition(query);
        List<LocationTrackingVO> voList = rawPage.getData().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(rawPage.getPageNum(), rawPage.getPageSize(), rawPage.getTotal(), voList);
    }

    /**
     * Entity 轉 VO（回傳給前端）
     */
    private LocationTrackingVO toVO(LocationTracking entity) {
        LocationTrackingVO vo = new LocationTrackingVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
