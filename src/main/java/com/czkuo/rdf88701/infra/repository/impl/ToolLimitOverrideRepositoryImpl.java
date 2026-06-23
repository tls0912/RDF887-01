package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.ToolLimitOverrideRepository;
import com.czkuo.rdf88701.infra.entity.ToolLimitOverride;
import com.czkuo.rdf88701.infra.mapper.ToolLimitOverrideMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ToolLimitOverrideRepositoryImpl implements ToolLimitOverrideRepository {

    private final ToolLimitOverrideMapper toolLimitOverrideMapper;

    public ToolLimitOverrideRepositoryImpl(ToolLimitOverrideMapper toolLimitOverrideMapper) {
        this.toolLimitOverrideMapper = toolLimitOverrideMapper;
    }

    @Override
    public Optional<ToolLimitOverride> findById(Long id) {
        return Optional.ofNullable(toolLimitOverrideMapper.selectById(id));
    }

    @Override
    public boolean save(ToolLimitOverride entity) {
        return toolLimitOverrideMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(ToolLimitOverride entity) {
        return toolLimitOverrideMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return toolLimitOverrideMapper.deleteById(id) > 0;
    }

    @Override
    public List<ToolLimitOverride> findAll() {
        return toolLimitOverrideMapper.selectList(new QueryWrapper<>());
    }
}
