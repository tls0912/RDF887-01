package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.WorkingBeamRepository;
import com.czkuo.rdf88701.infra.entity.WorkingBeam;
import com.czkuo.rdf88701.infra.mapper.WorkingBeamMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class WorkingBeamRepositoryImpl implements WorkingBeamRepository {

    private final WorkingBeamMapper workingBeamMapper;

    public WorkingBeamRepositoryImpl(WorkingBeamMapper workingBeamMapper) {
        this.workingBeamMapper = workingBeamMapper;
    }

    @Override
    public Optional<WorkingBeam> findById(Long id) {
        return Optional.ofNullable(workingBeamMapper.selectById(id));
    }

    @Override
    public boolean save(WorkingBeam entity) {
        return workingBeamMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(WorkingBeam entity) {
        return workingBeamMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return workingBeamMapper.deleteById(id) > 0;
    }

    @Override
    public List<WorkingBeam> findAll() {
        return workingBeamMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public List<WorkingBeam> findEnabledBeams() {
        return workingBeamMapper.selectList(
                new QueryWrapper<WorkingBeam>()
                        .eq("enabled", true)
        );
    }
}
