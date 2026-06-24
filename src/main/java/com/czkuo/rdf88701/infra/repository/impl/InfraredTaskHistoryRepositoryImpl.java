package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.InfraredTaskHistoryRepository;
import com.czkuo.rdf88701.infra.entity.InfraredTaskHistory;
import com.czkuo.rdf88701.infra.mapper.InfraredTaskHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class InfraredTaskHistoryRepositoryImpl implements InfraredTaskHistoryRepository {

    private final InfraredTaskHistoryMapper infraredTaskHistoryMapper;

    public InfraredTaskHistoryRepositoryImpl(InfraredTaskHistoryMapper infraredTaskHistoryMapper) {
        this.infraredTaskHistoryMapper = infraredTaskHistoryMapper;
    }

    @Override
    public Optional<InfraredTaskHistory> findById(Long id) {
        return Optional.ofNullable(infraredTaskHistoryMapper.selectById(id));
    }

    @Override
    public boolean save(InfraredTaskHistory entity) {
        return infraredTaskHistoryMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(InfraredTaskHistory entity) {
        return infraredTaskHistoryMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return infraredTaskHistoryMapper.deleteById(id) > 0;
    }

    @Override
    public List<InfraredTaskHistory> findAll() {
        return infraredTaskHistoryMapper.selectList(new QueryWrapper<>());
    }
}
