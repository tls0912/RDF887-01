package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.CraneTaskFollowUpRecordRepository;
import com.czkuo.rdf88701.infra.entity.CraneTaskFollowUpRecord;
import com.czkuo.rdf88701.infra.mapper.CraneTaskFollowUpRecordMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class CraneTaskFollowUpRecordRepositoryImpl implements CraneTaskFollowUpRecordRepository {

    private final CraneTaskFollowUpRecordMapper craneTaskFollowUpRecordMapper;

    public CraneTaskFollowUpRecordRepositoryImpl(CraneTaskFollowUpRecordMapper craneTaskFollowUpRecordMapper) {
        this.craneTaskFollowUpRecordMapper = craneTaskFollowUpRecordMapper;
    }

    @Override
    public Optional<CraneTaskFollowUpRecord> findById(Long id) {
        return Optional.ofNullable(craneTaskFollowUpRecordMapper.selectById(id));
    }

    @Override
    public boolean save(CraneTaskFollowUpRecord entity) {
        return craneTaskFollowUpRecordMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(CraneTaskFollowUpRecord entity) {
        return craneTaskFollowUpRecordMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return craneTaskFollowUpRecordMapper.deleteById(id) > 0;
    }

    @Override
    public List<CraneTaskFollowUpRecord> findAll() {
        return craneTaskFollowUpRecordMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public Optional<CraneTaskFollowUpRecord> findOriginalTaskId(Long taskId) {
        QueryWrapper<CraneTaskFollowUpRecord> query = new QueryWrapper<>();
        query.eq("original_task_id", taskId)
                .orderByDesc("created_time")
                .last("LIMIT 1");
        return Optional.ofNullable(craneTaskFollowUpRecordMapper.selectOne(query));
    }

    @Override
    public Optional<CraneTaskFollowUpRecord> findByFollowUpTaskId(Long taskId) {
        QueryWrapper<CraneTaskFollowUpRecord> query = new QueryWrapper<>();
        query.eq("follow_up_task_id", taskId)
                .orderByDesc("created_time")
                .last("LIMIT 1");
        return Optional.ofNullable(craneTaskFollowUpRecordMapper.selectOne(query));
    }
}
