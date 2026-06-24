package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.czkuo.rdf88701.domain.repository.MqttEventLogRepository;
import com.czkuo.rdf88701.infra.entity.MqttEventLog;
import com.czkuo.rdf88701.infra.mapper.MqttEventLogMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class MqttEventLogRepositoryImpl implements MqttEventLogRepository {

    private final MqttEventLogMapper mapper;

    public MqttEventLogRepositoryImpl(MqttEventLogMapper mapper) {
        this.mapper = mapper;
    }

    /* ===== 基本 CRUD ===== */

    @Override
    public Optional<MqttEventLog> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public Optional<MqttEventLog> findByTid(String tid) {
        return Optional.ofNullable(
                mapper.selectOne(
                        new LambdaQueryWrapper<MqttEventLog>()
                                .eq(MqttEventLog::getTid, tid)
                                .last("LIMIT 1")
                )
        );
    }

    @Override
    public boolean save(MqttEventLog entity) {
        return mapper.insert(entity) > 0;
    }

    @Override
    public boolean update(MqttEventLog entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<MqttEventLog> findAll() {
        return mapper.selectList(new LambdaQueryWrapper<>());
    }

    /* ===== 補償/排程 需要的方法 ===== */

    @Override
    public List<MqttEventLog> findDueForSend(LocalDateTime now, int limit) {
        int lim = Math.max(1, limit);
        return mapper.selectList(
                new LambdaQueryWrapper<MqttEventLog>()
                        .in(MqttEventLog::getStatus, Arrays.asList("PENDING", "RETRYING"))
                        .le(MqttEventLog::getNextAttemptTime, now)
                        .orderByAsc(MqttEventLog::getNextAttemptTime)
                        .orderByAsc(MqttEventLog::getId)
                        .last("LIMIT " + lim)
        );
    }

    @Override
    public List<MqttEventLog> findWaitingAckOverdue(LocalDateTime now, int limit) {
        int lim = Math.max(1, limit);
        return mapper.selectList(
                new LambdaQueryWrapper<MqttEventLog>()
                        .eq(MqttEventLog::getStatus, "SENT")
                        .eq(MqttEventLog::getRequireAck, true)
                        .le(MqttEventLog::getNextAttemptTime, now)
                        .orderByAsc(MqttEventLog::getNextAttemptTime)
                        .orderByAsc(MqttEventLog::getId)
                        .last("LIMIT " + lim)
        );
    }

    @Override
    public boolean tryMarkSent(Long id,
                               String expectStatus,
                               LocalDateTime sendTime,
                               LocalDateTime nextAttemptTime) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<MqttEventLog> uw = new LambdaUpdateWrapper<MqttEventLog>()
                .eq(MqttEventLog::getId, id)
                .eq(MqttEventLog::getStatus, expectStatus)
                .set(MqttEventLog::getStatus, "SENT")
                .set(MqttEventLog::getSendTime, sendTime)
                .set(MqttEventLog::getNextAttemptTime, nextAttemptTime)
                .set(MqttEventLog::getUpdatedTime, now);
        return mapper.update(null, uw) > 0;
    }

    @Override
    public boolean tryMarkAckedByTid(String tid,
                                     LocalDateTime ackTime,
                                     String resultMessage) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<MqttEventLog> uw = new LambdaUpdateWrapper<MqttEventLog>()
                .eq(MqttEventLog::getTid, tid)
                .in(MqttEventLog::getStatus, Arrays.asList("PENDING", "SENT", "RETRYING"))
                .set(MqttEventLog::getStatus, "ACKED")
                .set(MqttEventLog::getAckTime, ackTime)
                .set(MqttEventLog::getResultMessage, resultMessage)
                .set(MqttEventLog::getUpdatedTime, now);
        return mapper.update(null, uw) > 0;
    }

    @Override
    public boolean tryMarkRetrying(Long id,
                                   String expectStatus,
                                   int nextRetryCount,
                                   LocalDateTime nextAttemptTime,
                                   String resultMessage) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<MqttEventLog> uw = new LambdaUpdateWrapper<MqttEventLog>()
                .eq(MqttEventLog::getId, id)
                .eq(MqttEventLog::getStatus, expectStatus)
                .set(MqttEventLog::getStatus, "RETRYING")
                .set(MqttEventLog::getRetryCount, nextRetryCount)
                .set(MqttEventLog::getNextAttemptTime, nextAttemptTime)
                .set(MqttEventLog::getResultMessage, resultMessage)
                .set(MqttEventLog::getUpdatedTime, now);
        return mapper.update(null, uw) > 0;
    }

    @Override
    public boolean tryMarkFailed(Long id,
                                 String expectStatus,
                                 String resultMessage) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<MqttEventLog> uw = new LambdaUpdateWrapper<MqttEventLog>()
                .eq(MqttEventLog::getId, id)
                .eq(MqttEventLog::getStatus, expectStatus)
                .set(MqttEventLog::getStatus, "FAILED")
                .set(MqttEventLog::getResultMessage, resultMessage)
                .set(MqttEventLog::getUpdatedTime, now);
        return mapper.update(null, uw) > 0;
    }

    @Override
    public boolean tryMarkTimeout(Long id,
                                  String expectStatus,
                                  String resultMessage) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<MqttEventLog> uw = new LambdaUpdateWrapper<MqttEventLog>()
                .eq(MqttEventLog::getId, id)
                .eq(MqttEventLog::getStatus, expectStatus)  // 通常 expectStatus = "SENT"
                .set(MqttEventLog::getStatus, "TIMEOUT")
                .set(MqttEventLog::getResultMessage, resultMessage)
                .set(MqttEventLog::getUpdatedTime, now);
        return mapper.update(null, uw) > 0;
    }

    @Override
    public boolean updateNextAttemptTime(Long id, LocalDateTime nextAttemptTime) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<MqttEventLog> uw = new LambdaUpdateWrapper<MqttEventLog>()
                .eq(MqttEventLog::getId, id)
                .set(MqttEventLog::getNextAttemptTime, nextAttemptTime)
                .set(MqttEventLog::getUpdatedTime, now);
        return mapper.update(null, uw) > 0;
    }

    @Override
    public int countByStatus(String status) {
        Long cnt = mapper.selectCount(
                new LambdaQueryWrapper<MqttEventLog>()
                        .eq(MqttEventLog::getStatus, status)
        );
        return cnt == null ? 0 : cnt.intValue();
    }
}
