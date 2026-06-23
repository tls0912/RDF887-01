package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.czkuo.rdf88701.domain.repository.RobotR029TaskRepository;
import com.czkuo.rdf88701.infra.entity.RobotR029Task;
import com.czkuo.rdf88701.infra.mapper.RobotR029TaskMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class RobotR029TaskRepositoryImpl implements RobotR029TaskRepository {

    private final RobotR029TaskMapper robotR029TaskMapper;

    private static final List<String> TERMINALS = Arrays.asList("END", "NG", "CANCEL");

    public RobotR029TaskRepositoryImpl(RobotR029TaskMapper robotR029TaskMapper) {
        this.robotR029TaskMapper = robotR029TaskMapper;
    }

    @Override
    public Optional<RobotR029Task> findById(Long id) {
        return Optional.ofNullable(robotR029TaskMapper.selectById(id));
    }

    @Override
    public Optional<RobotR029Task> findByLogId(Long logId) {
        if (logId == null) return Optional.empty();
        QueryWrapper<RobotR029Task> qw = new QueryWrapper<>();
        qw.eq("log_id", logId).last("LIMIT 1");
        return Optional.ofNullable(robotR029TaskMapper.selectOne(qw));
    }

    @Override
    public Optional<RobotR029Task> findLatestByTid(String tid) {
        if (StringUtils.isBlank(tid)) return Optional.empty();
        QueryWrapper<RobotR029Task> qw = new QueryWrapper<>();
        qw.eq("tid", tid).orderByDesc("created_time").last("LIMIT 1");
        return Optional.ofNullable(robotR029TaskMapper.selectOne(qw));
    }

    @Override
    public List<RobotR029Task> findOpen() {
        QueryWrapper<RobotR029Task> qw = new QueryWrapper<>();
        // 未終結：RESULT 為 NULL 或者 不在('END','FAIL','CANCEL')
        // 生成： (external_last_result IS NULL OR external_last_result NOT IN (...))
        qw.isNull("external_last_result")
                .or(wrapper -> wrapper.notIn("external_last_result", TERMINALS))
                .orderByAsc("created_time")
                .orderByAsc("id");
        return robotR029TaskMapper.selectList(qw);
    }

    @Override
    public List<RobotR029Task> findOpenLimited(int limit) {
        QueryWrapper<RobotR029Task> qw = new QueryWrapper<>();
        qw.isNull("external_last_result")
                .or(wrapper -> wrapper.notIn("external_last_result", TERMINALS))
                .orderByAsc("created_time")
                .orderByAsc("id")
                .last("LIMIT " + Math.max(1, limit));
        return robotR029TaskMapper.selectList(qw);
    }

    @Override
    public List<RobotR029Task> findRecentSince(LocalDateTime since, int limit) {
        QueryWrapper<RobotR029Task> qw = new QueryWrapper<>();

        // 只取已終結任務（END / NG / CANCEL）
        qw.in("external_last_result", TERMINALS);

        if (since != null) {
            Timestamp ts = Timestamp.valueOf(since);
            // created_time >= since OR external_last_time >= since
            qw.and(w -> w.ge("created_time", ts)
                    .or()
                    .ge("external_last_time", ts));
        }

        qw.orderByDesc("created_time")
                .orderByDesc("id")
                .last("LIMIT " + Math.max(1, limit));

        return robotR029TaskMapper.selectList(qw);
    }

    @Override
    public boolean save(RobotR029Task entity) {
        return robotR029TaskMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(RobotR029Task entity) {
        return robotR029TaskMapper.updateById(entity) > 0;
    }

    @Override
    public boolean updateByLogId(RobotR029Task entity) {
        if (entity == null || entity.getLogId() == null) {
            return false; // 或丟 IllegalArgumentException 視你風格
        }
        UpdateWrapper<RobotR029Task> uw = new UpdateWrapper<>();
        uw.eq("log_id", entity.getLogId());
        // 只會更新 entity 中非 null 欄位（取決於 @TableField(updateStrategy=NOT_NULL) 或全域設定）
        return robotR029TaskMapper.update(entity, uw) > 0;
    }

    @Override
    public boolean updateInboxIdByLogId(Long logId, Long inboxId) {
        if (logId == null || inboxId == null) return false;
        return robotR029TaskMapper.update(
                null,
                new UpdateWrapper<RobotR029Task>()
                        .eq("log_id", logId)
                        .set("inbox_id", inboxId)
                        .set("updated_time", new java.sql.Timestamp(System.currentTimeMillis()))
        ) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return robotR029TaskMapper.deleteById(id) > 0;
    }

    @Override
    public List<RobotR029Task> findAll() {
        return robotR029TaskMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public int countProcessingByLane(String lane) {
        return robotR029TaskMapper.selectCount(
                new QueryWrapper<RobotR029Task>()
                        .eq("internal_state", "PROCESSING")
                        .eq("lane", lane)
        ).intValue();
    }

    @Override
    public boolean trySetLaneAndProcessingByLogId(Long logId, String lane) {
        // 僅允許 QUEUED & lane IS NULL 的情況下設 lane 並切 PROCESSING
        UpdateWrapper<RobotR029Task> uw = new UpdateWrapper<>();
        uw.eq("log_id", logId)
                .eq("internal_state", "QUEUED")
                .isNull("lane")
                .set("lane", lane)
                .set("internal_state", "PROCESSING")
                .set("external_last_result", "START")
                .set("external_last_time", new java.sql.Timestamp(System.currentTimeMillis()))
                .set("updated_time", new java.sql.Timestamp(System.currentTimeMillis()));
        return robotR029TaskMapper.update(null, uw) == 1;
    }

    @Override
    public boolean updateStateByLogId(Long logId, String fromState, String toState, String reason) {
        UpdateWrapper<RobotR029Task> uw = new UpdateWrapper<>();
        uw.eq("log_id", logId)
                .eq("internal_state", fromState)
                .set("internal_state", toState)
                .set("updated_time", new java.sql.Timestamp(System.currentTimeMillis()));
        if ("COMPLETED".equals(toState)) {
            uw.set("external_last_result", "END")
                    .set("external_last_time", new java.sql.Timestamp(System.currentTimeMillis()));
        } else if ("FAILED".equals(toState)) {
            uw.set("external_last_result", "NG")
                    .set("external_last_time", new java.sql.Timestamp(System.currentTimeMillis()))
                    .set("fail_reason", reason);
        }
        return robotR029TaskMapper.update(null, uw) == 1;
    }

    @Override
    public Optional<RobotR029Task> findFirstProcessingByLane(String lane) {
        if (StringUtils.isBlank(lane)) {
            return Optional.empty();
        }

        QueryWrapper<RobotR029Task> qw = new QueryWrapper<>();
        qw.eq("internal_state", "PROCESSING")
                .eq("lane", lane)
                .orderByAsc("created_time")
                .orderByAsc("id")
                .last("LIMIT 1");

        return Optional.ofNullable(robotR029TaskMapper.selectOne(qw));
    }
}
