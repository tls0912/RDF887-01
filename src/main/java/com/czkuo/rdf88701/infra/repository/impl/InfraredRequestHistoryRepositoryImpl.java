package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.InfraredRequestHistoryRepository;
import com.czkuo.rdf88701.infra.entity.InfraredRequestHistory;
import com.czkuo.rdf88701.infra.mapper.InfraredRequestHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class InfraredRequestHistoryRepositoryImpl implements InfraredRequestHistoryRepository {

    private final InfraredRequestHistoryMapper infraredRequestHistoryMapper;

    public InfraredRequestHistoryRepositoryImpl(InfraredRequestHistoryMapper infraredRequestHistoryMapper) {
        this.infraredRequestHistoryMapper = infraredRequestHistoryMapper;
    }

    @Override
    public Optional<InfraredRequestHistory> findById(Long id) {
        return Optional.ofNullable(infraredRequestHistoryMapper.selectById(id));
    }

    @Override
    public boolean save(InfraredRequestHistory entity) {
        return infraredRequestHistoryMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(InfraredRequestHistory entity) {
        return infraredRequestHistoryMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return infraredRequestHistoryMapper.deleteById(id) > 0;
    }

    @Override
    public List<InfraredRequestHistory> findAll() {
        return infraredRequestHistoryMapper.selectList(new QueryWrapper<>());
    }
}
