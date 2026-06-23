package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.RobotInR031Repository;
import com.czkuo.rdf88701.infra.entity.RobotInR031;
import com.czkuo.rdf88701.infra.mapper.RobotInR031Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RobotInR031RepositoryImpl implements RobotInR031Repository {

    private final RobotInR031Mapper robotInR031Mapper;

    public RobotInR031RepositoryImpl(RobotInR031Mapper robotInR031Mapper) {
        this.robotInR031Mapper = robotInR031Mapper;
    }

    @Override
    public Optional<RobotInR031> findById(Long id) {
        return Optional.ofNullable(robotInR031Mapper.selectById(id));
    }

    @Override
    public boolean save(RobotInR031 entity) {
        return robotInR031Mapper.insert(entity) > 0;
    }

    @Override
    public boolean update(RobotInR031 entity) {
        return robotInR031Mapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return robotInR031Mapper.deleteById(id) > 0;
    }

    @Override
    public List<RobotInR031> findAll() {
        return robotInR031Mapper.selectList(new QueryWrapper<>());
    }
}
