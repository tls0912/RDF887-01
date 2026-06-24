package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.InfraredRepository;
import com.czkuo.rdf88701.infra.entity.Infrared;
import com.czkuo.rdf88701.infra.mapper.InfraredMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class InfraredRepositoryImpl implements InfraredRepository {

    private final InfraredMapper infraredMapper;

    public InfraredRepositoryImpl(InfraredMapper infraredMapper) {
        this.infraredMapper = infraredMapper;
    }

    @Override
    public Optional<Infrared> findById(Long id) {
        return Optional.ofNullable(infraredMapper.selectById(id));
    }

    @Override
    public boolean save(Infrared entity) {
        return infraredMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(Infrared entity) {
        return infraredMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return infraredMapper.deleteById(id) > 0;
    }

    @Override
    public List<Infrared> findAll() {
        return infraredMapper.selectList(new QueryWrapper<>());
    }
}
