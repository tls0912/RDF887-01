package com.czkuo.rdf88701.application.service.query;

import com.czkuo.rdf88701.application.dto.query.LocationFlowQuery;
import com.czkuo.rdf88701.application.dto.vo.LocationFlowVO;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.domain.repository.LocationFlowRepository;
import com.czkuo.rdf88701.infra.entity.LocationFlow;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LocationFlow 查詢服務
 */
@Service
public class LocationFlowQueryService {

    private final LocationFlowRepository locationFlowRepository;

    public LocationFlowQueryService(LocationFlowRepository locationFlowRepository) {
        this.locationFlowRepository = locationFlowRepository;
    }

    /**
     * 根據查詢條件查詢 LocationFlow 分頁資料
     */
    public PageResult<LocationFlowVO> queryPage(LocationFlowQuery query) {
        PageResult<LocationFlow> result = locationFlowRepository.findPageByCondition(query);
        List<LocationFlowVO> voList = result.getData().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(result.getPageNum(), result.getPageSize(), result.getTotal(), voList);
    }

    /**
     * Entity 轉換為 VO
     */
    private LocationFlowVO toVO(LocationFlow entity) {
        LocationFlowVO vo = new LocationFlowVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
