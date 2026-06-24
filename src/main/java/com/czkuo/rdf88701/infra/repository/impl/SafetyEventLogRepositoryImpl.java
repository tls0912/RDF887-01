package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.SafetyEventLogRepository;
import com.czkuo.rdf88701.infra.entity.SafetyEventLog;
import com.czkuo.rdf88701.infra.mapper.SafetyEventLogMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * SafetyEventLogRepositoryImpl
 *
 * 對應資料表：safety_event_log
 * - 實作常用查詢（依點位、時間區間、最近 N 筆）、批次新增、統計、歷史清理
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
public class SafetyEventLogRepositoryImpl implements SafetyEventLogRepository {

    private final SafetyEventLogMapper safetyEventLogMapper;

    public SafetyEventLogRepositoryImpl(SafetyEventLogMapper safetyEventLogMapper) {
        this.safetyEventLogMapper = safetyEventLogMapper;
    }

    // ===================== 基本 CRUD =====================

    @Override
    public Optional<SafetyEventLog> findById(Long id) {
        return Optional.ofNullable(safetyEventLogMapper.selectById(id));
    }

    @Override
    public boolean save(SafetyEventLog entity) {
        if (entity == null) return false;
        return safetyEventLogMapper.insert(entity) > 0;
    }

    @Override
    public boolean saveBatch(List<SafetyEventLog> entities) {
        if (entities == null || entities.isEmpty()) return true; // 空清單視為成功
        int ok = 0;
        for (SafetyEventLog e : entities) {
            if (e == null) continue;
            ok += safetyEventLogMapper.insert(e);
        }
        return ok == entities.size();
    }

    @Override
    public boolean update(SafetyEventLog entity) {
        if (entity == null || entity.getId() == null) return false;
        return safetyEventLogMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        if (id == null) return false;
        return safetyEventLogMapper.deleteById(id) > 0;
    }

    @Override
    public List<SafetyEventLog> findAll() {
        List<SafetyEventLog> list = safetyEventLogMapper.selectList(new LambdaQueryWrapper<>());
        return list != null ? list : Collections.emptyList();
    }

    // ===================== 依點位查詢 =====================

    @Override
    public List<SafetyEventLog> findAllByPointId(Long pointId) {
        if (pointId == null) return Collections.emptyList();
        List<SafetyEventLog> list = safetyEventLogMapper.selectList(
                new LambdaQueryWrapper<SafetyEventLog>()
                        .eq(SafetyEventLog::getPointId, pointId)
                        .orderByAsc(SafetyEventLog::getChangeTime)
        );
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public List<SafetyEventLog> findByPointIdAndTimeRange(Long pointId, LocalDateTime from, LocalDateTime to) {
        if (pointId == null || from == null || to == null) return Collections.emptyList();
        List<SafetyEventLog> list = safetyEventLogMapper.selectList(
                new LambdaQueryWrapper<SafetyEventLog>()
                        .eq(SafetyEventLog::getPointId, pointId)
                        .between(SafetyEventLog::getChangeTime, from, to)
                        .orderByAsc(SafetyEventLog::getChangeTime)
        );
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public List<SafetyEventLog> findRecentByPointId(Long pointId, int limit) {
        if (pointId == null || limit <= 0) return Collections.emptyList();
        List<SafetyEventLog> list = safetyEventLogMapper.selectList(
                new LambdaQueryWrapper<SafetyEventLog>()
                        .eq(SafetyEventLog::getPointId, pointId)
                        .orderByDesc(SafetyEventLog::getChangeTime)
                        .last("LIMIT " + limit)
        );
        return list != null ? list : Collections.emptyList();
    }

    // ===================== 全域查詢/統計 =====================

    @Override
    public List<SafetyEventLog> findRecentAll(int limit) {
        if (limit <= 0) return Collections.emptyList();
        List<SafetyEventLog> list = safetyEventLogMapper.selectList(
                new LambdaQueryWrapper<SafetyEventLog>()
                        .orderByDesc(SafetyEventLog::getChangeTime)
                        .last("LIMIT " + limit)
        );
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public long count() {
        Long c = safetyEventLogMapper.selectCount(new LambdaQueryWrapper<>());
        return c != null ? c : 0L;
    }

    @Override
    public long countByPointId(Long pointId) {
        if (pointId == null) return 0L;
        Long c = safetyEventLogMapper.selectCount(
                new LambdaQueryWrapper<SafetyEventLog>()
                        .eq(SafetyEventLog::getPointId, pointId)
        );
        return c != null ? c : 0L;
    }

    // ===================== 歷史清理 =====================

    @Override
    public int deleteByPointIdBefore(Long pointId, LocalDateTime cutoff) {
        if (pointId == null || cutoff == null) return 0;
        return safetyEventLogMapper.delete(
                new LambdaQueryWrapper<SafetyEventLog>()
                        .eq(SafetyEventLog::getPointId, pointId)
                        .lt(SafetyEventLog::getChangeTime, cutoff)
        );
    }

    @Override
    public int deleteBefore(LocalDateTime cutoff) {
        if (cutoff == null) return 0;
        return safetyEventLogMapper.delete(
                new LambdaQueryWrapper<SafetyEventLog>()
                        .lt(SafetyEventLog::getChangeTime, cutoff)
        );
    }
}
