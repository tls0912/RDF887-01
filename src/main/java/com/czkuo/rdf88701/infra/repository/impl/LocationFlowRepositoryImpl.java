package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.application.dto.query.LocationFlowQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.common.enums.ExitType;
import com.czkuo.rdf88701.common.util.LambdaQueryHelper;
import com.czkuo.rdf88701.domain.repository.LocationFlowRepository;
import com.czkuo.rdf88701.infra.entity.LocationFlow;
import com.czkuo.rdf88701.infra.mapper.LocationFlowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * LocationFlow 資料存取實作
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
@RequiredArgsConstructor
public class LocationFlowRepositoryImpl implements LocationFlowRepository {

    private final LocationFlowMapper locationFlowMapper;

    @Override
    public Optional<LocationFlow> findById(Long id) {
        return Optional.ofNullable(locationFlowMapper.selectById(id));
    }

    @Override
    public List<LocationFlow> findByCondition(LocationFlowQuery query) {
        LambdaQueryHelper<LocationFlow> helper = buildQueryWrapper(query);
        int offset = (query.getPageNum() - 1) * query.getPageSize();
        helper.getWrapper().last("LIMIT " + offset + "," + query.getPageSize());
        return locationFlowMapper.selectList(helper.getWrapper());
    }

    @Override
    public PageResult<LocationFlow> findPageByCondition(LocationFlowQuery query) {
        LambdaQueryHelper<LocationFlow> helper = buildQueryWrapper(query);
        Page<LocationFlow> page = new Page<>(query.getSafePageNum(), query.getSafePageSize());
        IPage<LocationFlow> result = locationFlowMapper.selectPage(page, helper.getWrapper());
        return new PageResult<>(query.getSafePageNum(), query.getSafePageSize(), result.getTotal(), result.getRecords());
    }

    private LambdaQueryHelper<LocationFlow> buildQueryWrapper(LocationFlowQuery query) {
        return LambdaQueryHelper.<LocationFlow>of()
                .eqIfPresent(LocationFlow::getId, query::getId)
                .inIfPresent(LocationFlow::getId, query::getIdList)
                .eqIfPresent(LocationFlow::getContainerMainId, query::getContainerMainId)
                .eqIfPresent(LocationFlow::getLocationPointId, query::getLocationPointId)
                .eqIfPresent(LocationFlow::getEntryType, query::getEntryType)
                .eqIfPresent(LocationFlow::getExitType, query::getExitType)
                .geIfPresent(LocationFlow::getArrivedTime, query::getArrivedAfter)
                .leIfPresent(LocationFlow::getArrivedTime, query::getArrivedBefore)
                .geIfPresent(LocationFlow::getLeftTime, query::getLeftAfter)
                .leIfPresent(LocationFlow::getLeftTime, query::getLeftBefore);
    }

    @Override
    public boolean save(LocationFlow entity) {
        return locationFlowMapper.insert(entity) > 0;
    }

    @Override
    public boolean insert(LocationFlow entity) {
        return locationFlowMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(LocationFlow entity) {
        return locationFlowMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return locationFlowMapper.deleteById(id) > 0;
    }

    @Override
    public List<LocationFlow> findAll() {
        return locationFlowMapper.selectList(null);
    }

    @Override
    public boolean closeActiveFlow(Long containerMainId, Long sourceTaskId, String exitOperator) {
        LambdaQueryWrapper<LocationFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LocationFlow::getContainerMainId, containerMainId)
                .isNull(LocationFlow::getLeftTime)
                .orderByDesc(LocationFlow::getArrivedTime)
                .last("LIMIT 1");

        LocationFlow active = locationFlowMapper.selectOne(wrapper);
        if (active == null) return false;

        active.setLeftTime(LocalDateTime.now());
        active.setExitType("NORMAL");
        active.setExitOperator(exitOperator);
        active.setSourceTaskId(sourceTaskId);

        return locationFlowMapper.updateById(active) > 0;
    }

    @Override
    public int markPreviousAsLeft(Long containerMainId, LocalDateTime leftTime) {
        LambdaQueryWrapper<LocationFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LocationFlow::getContainerMainId, containerMainId)
                .isNull(LocationFlow::getLeftTime)
                .orderByDesc(LocationFlow::getArrivedTime)
                .last("LIMIT 1");

        LocationFlow active = locationFlowMapper.selectOne(wrapper);
        if (active == null) {
            return 0;
        }

        active.setLeftTime(leftTime);
        return locationFlowMapper.updateById(active);
    }

    @Override
    public boolean markExit(Long containerMainId, Long locationPointId, LocalDateTime leftTime, ExitType exitType, String exitOperator) {
        LambdaQueryWrapper<LocationFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LocationFlow::getContainerMainId, containerMainId)
                .eq(LocationFlow::getLocationPointId, locationPointId)
                .isNull(LocationFlow::getLeftTime)
                .orderByDesc(LocationFlow::getArrivedTime)
                .last("LIMIT 1");

        LocationFlow active = locationFlowMapper.selectOne(wrapper);
        if (active == null) return false;

        active.setLeftTime(leftTime);
        active.setExitType(exitType.name());
        active.setExitOperator(exitOperator);

        return locationFlowMapper.updateById(active) > 0;
    }
}
