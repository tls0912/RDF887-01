package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.RobotInR007Repository;
import com.czkuo.rdf88701.infra.entity.RobotInR007;
import com.czkuo.rdf88701.infra.mapper.RobotInR007Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RobotInR007RepositoryImpl implements RobotInR007Repository {

    private final RobotInR007Mapper robotInR007Mapper;

    public RobotInR007RepositoryImpl(RobotInR007Mapper robotInR007Mapper) {
        this.robotInR007Mapper = robotInR007Mapper;
    }

    @Override
    public Optional<RobotInR007> findById(Long id) {
        return Optional.ofNullable(robotInR007Mapper.selectById(id));
    }

    @Override
    public boolean save(RobotInR007 entity) {
        return robotInR007Mapper.insert(entity) > 0;
    }

    @Override
    public boolean update(RobotInR007 entity) {
        return robotInR007Mapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return robotInR007Mapper.deleteById(id) > 0;
    }

    @Override
    public List<RobotInR007> findAll() {
        return robotInR007Mapper.selectList(new QueryWrapper<>());
    }
}
