package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.WorkingBeamTaskHistoryRepository;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTaskHistory;
import com.czkuo.rdf88701.infra.mapper.WorkingBeamTaskHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class WorkingBeamTaskHistoryRepositoryImpl implements WorkingBeamTaskHistoryRepository {

    private final WorkingBeamTaskHistoryMapper workingBeamTaskHistoryMapper;

    public WorkingBeamTaskHistoryRepositoryImpl(WorkingBeamTaskHistoryMapper workingBeamTaskHistoryMapper) {
        this.workingBeamTaskHistoryMapper = workingBeamTaskHistoryMapper;
    }

    @Override
    public Optional<WorkingBeamTaskHistory> findById(Long id) {
        return Optional.ofNullable(workingBeamTaskHistoryMapper.selectById(id));
    }

    @Override
    public boolean save(WorkingBeamTaskHistory entity) {
        return workingBeamTaskHistoryMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(WorkingBeamTaskHistory entity) {
        return workingBeamTaskHistoryMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return workingBeamTaskHistoryMapper.deleteById(id) > 0;
    }

    @Override
    public List<WorkingBeamTaskHistory> findAll() {
        return workingBeamTaskHistoryMapper.selectList(new QueryWrapper<>());
    }
}
