package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.AlarmItemLogRepository;
import com.czkuo.rdf88701.infra.entity.AlarmItemLog;
import com.czkuo.rdf88701.infra.mapper.AlarmItemLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Repository
@RequiredArgsConstructor
public class AlarmItemLogRepositoryImpl implements AlarmItemLogRepository {

    private final AlarmItemLogMapper logMapper;

    // =========================
    // 基本 CRUD
    // =========================

    @Override
    public Optional<AlarmItemLog> findById(Long id) {
        return Optional.ofNullable(logMapper.selectById(id));
    }

    @Override
    public boolean save(AlarmItemLog entity) {
        return logMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(AlarmItemLog entity) {
        // 不建議於正式環境呼叫；若一定要用，請加上審批流程
        return logMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        // 不建議於正式環境呼叫；若一定要用，請加上審批流程
        return logMapper.deleteById(id) > 0;
    }

    @Override
    public List<AlarmItemLog> findAll() {
        return logMapper.selectList(new LambdaQueryWrapper<>());
    }

    // =========================
    // 常用查詢
    // =========================

    @Override
    public Optional<AlarmItemLog> findLastByGlobalCode(int globalCode) {
        return Optional.ofNullable(
                logMapper.selectOne(
                        new LambdaQueryWrapper<AlarmItemLog>()
                                .eq(AlarmItemLog::getGlobalCode, globalCode)
                                .orderByDesc(AlarmItemLog::getCreatedAt)
                                .last("LIMIT 1")
                )
        );
    }

    @Override
    public List<AlarmItemLog> findRecentByGlobalCode(int globalCode, int limit) {
        int n = Math.max(1, limit);
        return logMapper.selectList(
                new LambdaQueryWrapper<AlarmItemLog>()
                        .eq(AlarmItemLog::getGlobalCode, globalCode)
                        .orderByDesc(AlarmItemLog::getCreatedAt)
                        .last("LIMIT " + n)
        );
    }

    @Override
    public List<AlarmItemLog> findByGlobalCodeBetween(
            int globalCode, LocalDateTime from, LocalDateTime to, Collection<String> eventTypes) {

        LambdaQueryWrapper<AlarmItemLog> qw = new LambdaQueryWrapper<AlarmItemLog>()
                .eq(AlarmItemLog::getGlobalCode, globalCode)
                .ge(from != null, AlarmItemLog::getCreatedAt, from)
                .lt(to != null,   AlarmItemLog::getCreatedAt, to)
                .orderByAsc(AlarmItemLog::getCreatedAt);

        if (eventTypes != null && !eventTypes.isEmpty()) {
            qw.in(AlarmItemLog::getEventType, eventTypes);
        }
        return logMapper.selectList(qw);
    }

    @Override
    public List<AlarmItemLog> findRecentPlcQueueEvents(int limit) {
        int n = Math.max(1, limit);
        return logMapper.selectList(
                new LambdaQueryWrapper<AlarmItemLog>()
                        .in(AlarmItemLog::getEventType, List.of("PLC_ON", "PLC_OFF"))
                        .orderByDesc(AlarmItemLog::getCreatedAt)
                        .last("LIMIT " + n)
        );
    }

    @Override
    @Transactional
    public int saveBatch(List<AlarmItemLog> entities) {
        if (entities == null || entities.isEmpty()) return 0;
        int cnt = 0;
        for (AlarmItemLog e : entities) {
            cnt += logMapper.insert(e);
        }
        return cnt;
    }
}
