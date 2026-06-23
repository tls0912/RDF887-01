package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.ContainerDataHistoryRepository;
import com.czkuo.rdf88701.infra.entity.ContainerDataHistory;
import com.czkuo.rdf88701.infra.mapper.ContainerDataHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ContainerDataHistoryRepositoryImpl implements ContainerDataHistoryRepository {

    private final ContainerDataHistoryMapper containerDataHistoryMapper;

    public ContainerDataHistoryRepositoryImpl(ContainerDataHistoryMapper containerDataHistoryMapper) {
        this.containerDataHistoryMapper = containerDataHistoryMapper;
    }

    @Override
    public Optional<ContainerDataHistory> findById(Long id) {
        return Optional.ofNullable(containerDataHistoryMapper.selectById(id));
    }

    @Override
    public boolean save(ContainerDataHistory entity) {
        return containerDataHistoryMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(ContainerDataHistory entity) {
        return containerDataHistoryMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return containerDataHistoryMapper.deleteById(id) > 0;
    }

    @Override
    public List<ContainerDataHistory> findAll() {
        return containerDataHistoryMapper.selectList(new QueryWrapper<>());
    }
}
