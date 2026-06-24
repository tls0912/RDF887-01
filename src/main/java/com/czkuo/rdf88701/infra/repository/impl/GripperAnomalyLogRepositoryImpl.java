package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.GripperAnomalyLogRepository;
import com.czkuo.rdf88701.infra.entity.GripperAnomalyLog;
import com.czkuo.rdf88701.infra.mapper.GripperAnomalyLogMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class GripperAnomalyLogRepositoryImpl implements GripperAnomalyLogRepository {

    private final GripperAnomalyLogMapper gripperAnomalyLogMapper;

    public GripperAnomalyLogRepositoryImpl(GripperAnomalyLogMapper gripperAnomalyLogMapper) {
        this.gripperAnomalyLogMapper = gripperAnomalyLogMapper;
    }

    @Override
    public Optional<GripperAnomalyLog> findById(Long id) {
        return Optional.ofNullable(gripperAnomalyLogMapper.selectById(id));
    }

    @Override
    public boolean save(GripperAnomalyLog entity) {
        return gripperAnomalyLogMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(GripperAnomalyLog entity) {
        return gripperAnomalyLogMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return gripperAnomalyLogMapper.deleteById(id) > 0;
    }

    @Override
    public List<GripperAnomalyLog> findAll() {
        return gripperAnomalyLogMapper.selectList(new QueryWrapper<>());
    }
}
