package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.ToolStatusRepository;
import com.czkuo.rdf88701.infra.entity.ToolStatus;
import com.czkuo.rdf88701.infra.mapper.ToolStatusMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ToolStatusRepositoryImpl implements ToolStatusRepository {

    private final ToolStatusMapper toolStatusMapper;

    public ToolStatusRepositoryImpl(ToolStatusMapper toolStatusMapper) {
        this.toolStatusMapper = toolStatusMapper;
    }

    @Override
    public Optional<ToolStatus> findById(Long id) {
        return Optional.ofNullable(toolStatusMapper.selectById(id));
    }

    @Override
    public boolean save(ToolStatus entity) {
        return toolStatusMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(ToolStatus entity) {
        return toolStatusMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return toolStatusMapper.deleteById(id) > 0;
    }

    @Override
    public List<ToolStatus> findAll() {
        return toolStatusMapper.selectList(new QueryWrapper<>());
    }
}
