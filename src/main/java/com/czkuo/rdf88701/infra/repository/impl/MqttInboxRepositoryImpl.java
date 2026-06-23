package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.MqttInboxRepository;
import com.czkuo.rdf88701.infra.entity.MqttInbox;
import com.czkuo.rdf88701.infra.mapper.MqttInboxMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MqttInboxRepositoryImpl implements MqttInboxRepository {

    private final MqttInboxMapper mqttInboxMapper;

    public MqttInboxRepositoryImpl(MqttInboxMapper mqttInboxMapper) {
        this.mqttInboxMapper = mqttInboxMapper;
    }

    // ===== 基本 CRUD =====

    @Override
    public Optional<MqttInbox> findById(Long id) {
        return Optional.ofNullable(mqttInboxMapper.selectById(id));
    }

    @Override
    public boolean save(MqttInbox entity) {
        return mqttInboxMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(MqttInbox entity) {
        return mqttInboxMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return mqttInboxMapper.deleteById(id) > 0;
    }

    @Override
    public List<MqttInbox> findAll() {
        return mqttInboxMapper.selectList(new QueryWrapper<>());
    }

    /**
     * （舊）S074 觀測範圍；保留以免破壞相依
     */
    @Override
    public List<MqttInbox> findOpenForS074() {
        return mqttInboxMapper.selectList(
                new QueryWrapper<MqttInbox>()
                        .in("cmd_id", List.of("R007","R008","R029","R031"))
                        .in("process_state", List.of("RECEIVED","VALIDATED","PARSED","QUEUED","IN_PROGRESS"))
                        .orderByAsc("priority")
                        .orderByAsc("recv_time")
                        .orderByAsc("id")
        );
    }

    @Override
    public Optional<MqttInbox> findLatestByTid(String tid) {
        if (tid == null || tid.isBlank()) return Optional.empty();
        return Optional.ofNullable(
                mqttInboxMapper.selectOne(
                        new QueryWrapper<MqttInbox>()
                                .eq("tid", tid.trim())
                                .orderByDesc("created_time")
                                .orderByDesc("id")
                                .last("LIMIT 1")
                )
        );
    }

    @Override
    public Optional<MqttInbox> findLatestByCmdIdAndTid(String cmdId, String tid) {
        if (cmdId == null || cmdId.isBlank() || tid == null || tid.isBlank()) return Optional.empty();
        return Optional.ofNullable(
                mqttInboxMapper.selectOne(
                        new QueryWrapper<MqttInbox>()
                                .eq("cmd_id", cmdId.trim().toUpperCase())
                                .eq("tid", tid.trim())
                                .orderByDesc("created_time")
                                .orderByDesc("id")
                                .last("LIMIT 1")
                )
        );
    }

    // ===== 佇列操作 =====

    @Override
    @Transactional
    public Long enqueueFromInbound(Long logId, String tid, String cmdId,
                                   String sender, String receiver, String topic,
                                   LocalDateTime recvTime, int priority) {
        MqttInbox row = new MqttInbox();
        row.setLogId(logId);
        row.setTid(tid);
        row.setCmdId(cmdId);
        row.setSender(sender);
        row.setReceiver(receiver);
        row.setTopic(topic);
        row.setRecvTime(recvTime);
        row.setProcessState("RECEIVED");
        row.setPriority((byte) Math.max(1, Math.min(9, priority)));
        row.setAttempts(0);
        row.setNextAttemptTime(LocalDateTime.now());

        // 依 UNIQUE KEY (log_id) 防重
        mqttInboxMapper.insertIgnore(row);
        Long id = mqttInboxMapper.selectIdByLogId(logId);
        if (id == null) throw new IllegalStateException("enqueue failed, logId=" + logId);
        return id;
    }

    /**
     * 一般領取：不過濾 cmdId
     * - SQL 內含 FOR UPDATE SKIP LOCKED，避免多執行緒/多節點搶同一筆
     */
    @Override
    @Transactional
    public Optional<MqttInbox> pickOneForProcessing(String workerId, Duration lockTtl) {
        Long id = mqttInboxMapper.selectCandidateIdForUpdate(); // 已加 FOR UPDATE SKIP LOCKED
        if (id == null) return Optional.empty();

        int ok = mqttInboxMapper.updateToInProgress(id, workerId, Math.max(1, (int) lockTtl.getSeconds()));
        if (ok != 1) return Optional.empty(); // 被別人搶到

        return Optional.ofNullable(mqttInboxMapper.selectById(id));
    }

    /**
     * 只挑指定 cmdId（例：R029）進行領取
     */
    @Override
    @Transactional
    public Optional<MqttInbox> pickOneForProcessingByCmd(String cmdId, String workerId, Duration lockTtl) {
        Long id = mqttInboxMapper.selectCandidateIdForUpdateByCmd(cmdId); // 需在 Mapper/XML 新增
        if (id == null) return Optional.empty();

        int ok = mqttInboxMapper.updateToInProgress(id, workerId, Math.max(1, (int) lockTtl.getSeconds()));
        if (ok != 1) return Optional.empty();

        return Optional.ofNullable(mqttInboxMapper.selectById(id));
    }

    @Override
    @Transactional
    public Optional<MqttInbox> pickOneForProcessingByCmdNoNextAttemptTime(String cmdId, String workerId, Duration lockTtl) {
        Long id = mqttInboxMapper.selectCandidateIdForUpdateByCmdNoNextAttemptTime(cmdId); // 需在 Mapper/XML 新增
        if (id == null) return Optional.empty();

        int ok = mqttInboxMapper.updateToInProgressNoNextAttemptTime(id, workerId, Math.max(1, (int) lockTtl.getSeconds()));
        if (ok != 1) return Optional.empty();

        return Optional.ofNullable(mqttInboxMapper.selectById(id));
    }
    @Override
    public boolean updatePriority(Long id, int priority) {
        int p = Math.max(1, Math.min(9, priority));
        return mqttInboxMapper.updatePriority(id, p) == 1; // 需在 Mapper/XML 新增
    }

    @Override
    public boolean markQueued(Long id) {
        return mqttInboxMapper.markQueued(id) == 1;
    }

    @Override
    public boolean markDone(Long id, String mappedTaskType, Long mappedTaskId) {
        return mqttInboxMapper.markDone(id, mappedTaskType, mappedTaskId) == 1;
    }

    @Override
    public boolean markRejected(Long id, String reason) {
        return mqttInboxMapper.markRejected(id, reason) == 1;
    }

    @Override
    public boolean markCancelled(Long id, String reason) {
        return mqttInboxMapper.markCancelled(id, reason) == 1;
    }

    @Override
    public int releaseExpiredLocks() {
        return mqttInboxMapper.releaseExpiredLocks();
    }

    @Override
    public boolean requeue(Long id, Duration backoff) {
        int seconds = Math.max(1, (int) backoff.getSeconds());
        return mqttInboxMapper.requeueWithBackoff(id, seconds) == 1;
    }
}
