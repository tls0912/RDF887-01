package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.WorkingBeamControlRangeRepository;
import com.czkuo.rdf88701.infra.entity.WorkingBeamControlRange;
import com.czkuo.rdf88701.infra.mapper.WorkingBeamControlRangeMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * WorkingBeam 控制範圍 Repository 實作
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
public class WorkingBeamControlRangeRepositoryImpl implements WorkingBeamControlRangeRepository {

    private final WorkingBeamControlRangeMapper workingBeamControlRangeMapper;

    public WorkingBeamControlRangeRepositoryImpl(WorkingBeamControlRangeMapper workingBeamControlRangeMapper) {
        this.workingBeamControlRangeMapper = workingBeamControlRangeMapper;
    }

    @Override
    public Optional<WorkingBeamControlRange> findById(Long id) {
        return Optional.ofNullable(workingBeamControlRangeMapper.selectById(id));
    }

    @Override
    public boolean save(WorkingBeamControlRange entity) {
        return workingBeamControlRangeMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(WorkingBeamControlRange entity) {
        return workingBeamControlRangeMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return workingBeamControlRangeMapper.deleteById(id) > 0;
    }

    @Override
    public List<WorkingBeamControlRange> findAll() {
        return workingBeamControlRangeMapper.selectList(new QueryWrapper<>());
    }

    /**
     * 查詢指定 WorkingBeam 控制的所有位置（不排序）
     */
    @Override
    public List<WorkingBeamControlRange> findByWorkingBeamId(Long workingBeamId) {
        return workingBeamControlRangeMapper.selectList(
                new QueryWrapper<WorkingBeamControlRange>()
                        .eq("working_beam_id", workingBeamId)
        );
    }

    /**
     * 查詢指定 WorkingBeam 控制範圍，依照位移順序排序
     */
    @Override
    public List<WorkingBeamControlRange> findByWorkingBeamIdOrderByPositionOrder(Long workingBeamId) {
        return workingBeamControlRangeMapper.selectList(
                new QueryWrapper<WorkingBeamControlRange>()
                        .eq("working_beam_id", workingBeamId)
                        .orderByAsc("position_order")
        );
    }

    /**
     * 根據單一位置 ID 反查對應的 WorkingBeam 控制範圍
     */
    @Override
    public Optional<WorkingBeamControlRange> findByLocationPointId(Long locationPointId) {
        return Optional.ofNullable(
                workingBeamControlRangeMapper.selectOne(
                        new QueryWrapper<WorkingBeamControlRange>()
                                .eq("location_point_id", locationPointId)
                )
        );
    }
}
