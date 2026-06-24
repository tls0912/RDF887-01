package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.RobotInR029Repository;
import com.czkuo.rdf88701.infra.entity.RobotInR029;
import com.czkuo.rdf88701.infra.mapper.RobotInR029Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class RobotInR029RepositoryImpl implements RobotInR029Repository {

    private final RobotInR029Mapper robotInR029Mapper;

    public RobotInR029RepositoryImpl(RobotInR029Mapper robotInR029Mapper) {
        this.robotInR029Mapper = robotInR029Mapper;
    }

    @Override
    public Optional<RobotInR029> findById(Long id) {
        return Optional.ofNullable(robotInR029Mapper.selectById(id));
    }

    @Override
    public boolean save(RobotInR029 entity) {
        return robotInR029Mapper.insert(entity) > 0;
    }

    @Override
    public boolean update(RobotInR029 entity) {
        return robotInR029Mapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return robotInR029Mapper.deleteById(id) > 0;
    }

    @Override
    public List<RobotInR029> findAll() {
        return robotInR029Mapper.selectList(new QueryWrapper<>());
    }
}
