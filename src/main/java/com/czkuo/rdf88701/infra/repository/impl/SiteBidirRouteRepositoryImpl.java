package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.SiteBidirRouteRepository;
import com.czkuo.rdf88701.infra.entity.SiteBidirRoute;
import com.czkuo.rdf88701.infra.mapper.SiteBidirRouteMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class SiteBidirRouteRepositoryImpl implements SiteBidirRouteRepository {

    private final SiteBidirRouteMapper siteBidirRouteMapper;

    public SiteBidirRouteRepositoryImpl(SiteBidirRouteMapper siteBidirRouteMapper) {
        this.siteBidirRouteMapper = siteBidirRouteMapper;
    }

    @Override
    public Optional<SiteBidirRoute> findById(Long id) {
        return Optional.ofNullable(siteBidirRouteMapper.selectById(id));
    }

    @Override
    public boolean save(SiteBidirRoute entity) {
        return siteBidirRouteMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(SiteBidirRoute entity) {
        return siteBidirRouteMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return siteBidirRouteMapper.deleteById(id) > 0;
    }

    @Override
    public List<SiteBidirRoute> findAll() {
        return siteBidirRouteMapper.selectList(new QueryWrapper<>());
    }
}
