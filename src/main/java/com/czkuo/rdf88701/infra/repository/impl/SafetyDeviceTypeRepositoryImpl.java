package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.SafetyDeviceTypeRepository;
import com.czkuo.rdf88701.infra.entity.SafetyDeviceType;
import com.czkuo.rdf88701.infra.mapper.SafetyDeviceTypeMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SafetyDeviceTypeRepositoryImpl implements SafetyDeviceTypeRepository {

    private final SafetyDeviceTypeMapper safetyDeviceTypeMapper;

    public SafetyDeviceTypeRepositoryImpl(SafetyDeviceTypeMapper safetyDeviceTypeMapper) {
        this.safetyDeviceTypeMapper = safetyDeviceTypeMapper;
    }

    @Override
    public Optional<SafetyDeviceType> findById(Long id) {
        return Optional.ofNullable(safetyDeviceTypeMapper.selectById(id));
    }

    @Override
    public boolean save(SafetyDeviceType entity) {
        return safetyDeviceTypeMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(SafetyDeviceType entity) {
        return safetyDeviceTypeMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return safetyDeviceTypeMapper.deleteById(id) > 0;
    }

    @Override
    public List<SafetyDeviceType> findAll() {
        return safetyDeviceTypeMapper.selectList(new QueryWrapper<>());
    }
}
