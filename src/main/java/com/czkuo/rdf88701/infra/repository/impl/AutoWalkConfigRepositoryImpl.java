package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.AutoWalkConfigRepository;
import com.czkuo.rdf88701.infra.entity.AutoWalkConfig;
import com.czkuo.rdf88701.infra.mapper.AutoWalkConfigMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AutoWalkConfigRepositoryImpl implements AutoWalkConfigRepository {

    private final AutoWalkConfigMapper autoWalkConfigMapper;

    public AutoWalkConfigRepositoryImpl(AutoWalkConfigMapper autoWalkConfigMapper) {
        this.autoWalkConfigMapper = autoWalkConfigMapper;
    }

    @Override
    public Optional<AutoWalkConfig> findById(Long id) {
        return Optional.ofNullable(autoWalkConfigMapper.selectById(id));
    }

    @Override
    public boolean save(AutoWalkConfig entity) {
        return autoWalkConfigMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(AutoWalkConfig entity) {
        return autoWalkConfigMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return autoWalkConfigMapper.deleteById(id) > 0;
    }

    @Override
    public List<AutoWalkConfig> findAll() {
        return autoWalkConfigMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public List<AutoWalkConfig> findEnabledConfigs() {
        return autoWalkConfigMapper.selectEnabledConfigs();
    }
}
