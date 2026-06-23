package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.GripperTaskHistoryRepository;
import com.czkuo.rdf88701.infra.entity.GripperTaskHistory;
import com.czkuo.rdf88701.infra.mapper.GripperTaskHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class GripperTaskHistoryRepositoryImpl implements GripperTaskHistoryRepository {

    private final GripperTaskHistoryMapper gripperTaskHistoryMapper;

    public GripperTaskHistoryRepositoryImpl(GripperTaskHistoryMapper gripperTaskHistoryMapper) {
        this.gripperTaskHistoryMapper = gripperTaskHistoryMapper;
    }

    @Override
    public Optional<GripperTaskHistory> findById(Long id) {
        return Optional.ofNullable(gripperTaskHistoryMapper.selectById(id));
    }

    @Override
    public boolean save(GripperTaskHistory entity) {
        return gripperTaskHistoryMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(GripperTaskHistory entity) {
        return gripperTaskHistoryMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return gripperTaskHistoryMapper.deleteById(id) > 0;
    }

    @Override
    public List<GripperTaskHistory> findAll() {
        return gripperTaskHistoryMapper.selectList(new QueryWrapper<>());
    }
}
