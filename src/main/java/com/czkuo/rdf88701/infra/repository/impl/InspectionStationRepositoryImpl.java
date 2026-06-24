package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.InspectionStationRepository;
import com.czkuo.rdf88701.infra.entity.InspectionStation;
import com.czkuo.rdf88701.infra.mapper.InspectionStationMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class InspectionStationRepositoryImpl implements InspectionStationRepository {

    private final InspectionStationMapper inspectionStationMapper;

    public InspectionStationRepositoryImpl(InspectionStationMapper inspectionStationMapper) {
        this.inspectionStationMapper = inspectionStationMapper;
    }

    @Override
    public Optional<InspectionStation> findById(Long id) {
        return Optional.ofNullable(inspectionStationMapper.selectById(id));
    }

    @Override
    public boolean save(InspectionStation entity) {
        return inspectionStationMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(InspectionStation entity) {
        return inspectionStationMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return inspectionStationMapper.deleteById(id) > 0;
    }

    @Override
    public List<InspectionStation> findAll() {
        return inspectionStationMapper.selectList(new QueryWrapper<>());
    }
}
