package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.GripperRequestHistoryRepository;
import com.czkuo.rdf88701.infra.entity.GripperRequestHistory;
import com.czkuo.rdf88701.infra.mapper.GripperRequestHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class GripperRequestHistoryRepositoryImpl implements GripperRequestHistoryRepository {

    private final GripperRequestHistoryMapper gripperRequestHistoryMapper;

    public GripperRequestHistoryRepositoryImpl(GripperRequestHistoryMapper gripperRequestHistoryMapper) {
        this.gripperRequestHistoryMapper = gripperRequestHistoryMapper;
    }

    @Override
    public Optional<GripperRequestHistory> findById(Long id) {
        return Optional.ofNullable(gripperRequestHistoryMapper.selectById(id));
    }

    @Override
    public boolean save(GripperRequestHistory entity) {
        return gripperRequestHistoryMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(GripperRequestHistory entity) {
        return gripperRequestHistoryMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return gripperRequestHistoryMapper.deleteById(id) > 0;
    }

    @Override
    public List<GripperRequestHistory> findAll() {
        return gripperRequestHistoryMapper.selectList(new QueryWrapper<>());
    }
}
