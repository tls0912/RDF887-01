package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.RobotInR008Repository;
import com.czkuo.rdf88701.infra.entity.RobotInR008;
import com.czkuo.rdf88701.infra.mapper.RobotInR008Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class RobotInR008RepositoryImpl implements RobotInR008Repository {

    private final RobotInR008Mapper robotInR008Mapper;

    public RobotInR008RepositoryImpl(RobotInR008Mapper robotInR008Mapper) {
        this.robotInR008Mapper = robotInR008Mapper;
    }

    @Override
    public Optional<RobotInR008> findById(Long id) {
        return Optional.ofNullable(robotInR008Mapper.selectById(id));
    }

    @Override
    public boolean save(RobotInR008 entity) {
        return robotInR008Mapper.insert(entity) > 0;
    }

    @Override
    public boolean update(RobotInR008 entity) {
        return robotInR008Mapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return robotInR008Mapper.deleteById(id) > 0;
    }

    @Override
    public List<RobotInR008> findAll() {
        return robotInR008Mapper.selectList(new QueryWrapper<>());
    }
}
