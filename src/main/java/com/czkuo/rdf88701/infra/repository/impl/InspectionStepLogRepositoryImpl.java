package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.InspectionStepLogRepository;
import com.czkuo.rdf88701.infra.entity.InspectionStepLog;
import com.czkuo.rdf88701.infra.mapper.InspectionStepLogMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class InspectionStepLogRepositoryImpl implements InspectionStepLogRepository {

    private final InspectionStepLogMapper inspectionStepLogMapper;

    public InspectionStepLogRepositoryImpl(InspectionStepLogMapper inspectionStepLogMapper) {
        this.inspectionStepLogMapper = inspectionStepLogMapper;
    }

    @Override
    public Optional<InspectionStepLog> findById(Long id) {
        return Optional.ofNullable(inspectionStepLogMapper.selectById(id));
    }

    @Override
    public boolean save(InspectionStepLog entity) {
        return inspectionStepLogMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(InspectionStepLog entity) {
        return inspectionStepLogMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return inspectionStepLogMapper.deleteById(id) > 0;
    }

    @Override
    public List<InspectionStepLog> findAll() {
        return inspectionStepLogMapper.selectList(new QueryWrapper<>());
    }
}
