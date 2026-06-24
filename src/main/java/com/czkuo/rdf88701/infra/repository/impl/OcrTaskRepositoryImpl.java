package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.OcrTaskRepository;
import com.czkuo.rdf88701.infra.entity.OcrTask;
import com.czkuo.rdf88701.infra.mapper.OcrTaskMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class OcrTaskRepositoryImpl implements OcrTaskRepository {

    private final OcrTaskMapper ocrTaskMapper;

    public OcrTaskRepositoryImpl(OcrTaskMapper ocrTaskMapper) {
        this.ocrTaskMapper = ocrTaskMapper;
    }

    // —— 既有 CRUD —— //

    @Override
    public Optional<OcrTask> findById(Long id) {
        return Optional.ofNullable(ocrTaskMapper.selectById(id));
    }

    @Override
    public boolean save(OcrTask entity) {
        return ocrTaskMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(OcrTask entity) {
        return ocrTaskMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return ocrTaskMapper.deleteById(id) > 0;
    }

    @Override
    public List<OcrTask> findAll() {
        return ocrTaskMapper.selectList(new LambdaQueryWrapper<>());
    }

    // —— 新增查詢 —— //

    private static final List<String> FINAL_STATUSES = List.of("COMPLETED", "FAILED", "CANCELLED");

    @Override
    public Optional<OcrTask> findLatestByContainerId(Long containerMainId) {
        if (containerMainId == null) return Optional.empty();
        LambdaQueryWrapper<OcrTask> qw = new LambdaQueryWrapper<OcrTask>()
                .eq(OcrTask::getContainerMainId, containerMainId)
                .orderByDesc(OcrTask::getCreatedTime)
                .orderByDesc(OcrTask::getId) // 避免同時刻碰撞
                .last("LIMIT 1");
        List<OcrTask> list = ocrTaskMapper.selectList(qw);
        return (list == null || list.isEmpty()) ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public boolean existsUnfinishedForContainer(Long containerMainId) {
        if (containerMainId == null) return false;
        LambdaQueryWrapper<OcrTask> qw = new LambdaQueryWrapper<OcrTask>()
                .eq(OcrTask::getContainerMainId, containerMainId)
                .notIn(OcrTask::getStatus, FINAL_STATUSES)
                .last("LIMIT 1");
        List<OcrTask> list = ocrTaskMapper.selectList(qw);
        return list != null && !list.isEmpty();
    }

    @Override
    public List<OcrTask> findUnfinished(int limit) {
        int rows = (limit <= 0) ? 50 : limit;
        LambdaQueryWrapper<OcrTask> qw = new LambdaQueryWrapper<OcrTask>()
                .notIn(OcrTask::getStatus, FINAL_STATUSES)
                .orderByAsc(OcrTask::getCreatedTime)
                .orderByAsc(OcrTask::getId)
                .last("LIMIT " + rows);
        return ocrTaskMapper.selectList(qw);
    }
}
