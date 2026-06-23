package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.ToolCatalogRepository;
import com.czkuo.rdf88701.infra.entity.ToolCatalog;
import com.czkuo.rdf88701.infra.mapper.ToolCatalogMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ToolCatalogRepositoryImpl implements ToolCatalogRepository {

    private final ToolCatalogMapper toolCatalogMapper;

    public ToolCatalogRepositoryImpl(ToolCatalogMapper toolCatalogMapper) {
        this.toolCatalogMapper = toolCatalogMapper;
    }

    @Override
    public Optional<ToolCatalog> findById(Long id) {
        return Optional.ofNullable(toolCatalogMapper.selectById(id));
    }

    @Override
    public boolean save(ToolCatalog entity) {
        return toolCatalogMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(ToolCatalog entity) {
        return toolCatalogMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return toolCatalogMapper.deleteById(id) > 0;
    }

    @Override
    public List<ToolCatalog> findAll() {
        return toolCatalogMapper.selectList(new QueryWrapper<>());
    }
}
