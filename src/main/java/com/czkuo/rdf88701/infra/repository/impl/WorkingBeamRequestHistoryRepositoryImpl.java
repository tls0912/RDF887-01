package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.WorkingBeamRequestHistoryRepository;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequestHistory;
import com.czkuo.rdf88701.infra.mapper.WorkingBeamRequestHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class WorkingBeamRequestHistoryRepositoryImpl implements WorkingBeamRequestHistoryRepository {

    private final WorkingBeamRequestHistoryMapper workingBeamRequestHistoryMapper;

    public WorkingBeamRequestHistoryRepositoryImpl(WorkingBeamRequestHistoryMapper workingBeamRequestHistoryMapper) {
        this.workingBeamRequestHistoryMapper = workingBeamRequestHistoryMapper;
    }

    @Override
    public Optional<WorkingBeamRequestHistory> findById(Long id) {
        return Optional.ofNullable(workingBeamRequestHistoryMapper.selectById(id));
    }

    @Override
    public boolean save(WorkingBeamRequestHistory entity) {
        return workingBeamRequestHistoryMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(WorkingBeamRequestHistory entity) {
        return workingBeamRequestHistoryMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return workingBeamRequestHistoryMapper.deleteById(id) > 0;
    }

    @Override
    public List<WorkingBeamRequestHistory> findAll() {
        return workingBeamRequestHistoryMapper.selectList(new QueryWrapper<>());
    }
}
