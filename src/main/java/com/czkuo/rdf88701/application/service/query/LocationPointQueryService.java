package com.czkuo.rdf88701.application.service.query;

import com.czkuo.rdf88701.application.dto.query.LocationPointQuery;
import com.czkuo.rdf88701.application.dto.vo.LocationPointVO;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LocationPoint 查詢服務
 */
@Service
public class LocationPointQueryService {

    private final LocationPointRepository locationPointRepository;

    public LocationPointQueryService(LocationPointRepository locationPointRepository) {
        this.locationPointRepository = locationPointRepository;
    }

    /**
     * 根據查詢條件查詢 LocationPoint 分頁資料
     *
     * @param query 查詢條件
     * @return 分頁結果
     */
    public PageResult<LocationPointVO> queryPage(LocationPointQuery query) {
        var result = locationPointRepository.findPageByCondition(query);
        List<LocationPointVO> voList = result.getData().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(result.getPageNum(), result.getPageSize(), result.getTotal(), voList);
    }

    /**
     * Entity 轉 VO（回傳給前端）
     */
    private LocationPointVO toVO(LocationPoint entity) {
        LocationPointVO vo = new LocationPointVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
