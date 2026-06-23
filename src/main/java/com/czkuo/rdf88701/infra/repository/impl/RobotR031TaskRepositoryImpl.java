package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.czkuo.rdf88701.domain.repository.RobotR031TaskRepository;
import com.czkuo.rdf88701.infra.entity.RobotR031Task;
import com.czkuo.rdf88701.infra.mapper.RobotR031TaskMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class RobotR031TaskRepositoryImpl implements RobotR031TaskRepository {

    private final RobotR031TaskMapper robotR031TaskMapper;

    private static final List<String> TERMINALS = Arrays.asList("END", "FAIL", "CANCEL");

    public RobotR031TaskRepositoryImpl(RobotR031TaskMapper robotR031TaskMapper) {
        this.robotR031TaskMapper = robotR031TaskMapper;
    }

    @Override
    public Optional<RobotR031Task> findById(Long id) {
        return Optional.ofNullable(robotR031TaskMapper.selectById(id));
    }

    @Override
    public Optional<RobotR031Task> findByLogId(Long logId) {
        if (logId == null) return Optional.empty();
        QueryWrapper<RobotR031Task> qw = new QueryWrapper<>();
        qw.eq("log_id", logId).last("LIMIT 1");
        return Optional.ofNullable(robotR031TaskMapper.selectOne(qw));
    }

    @Override
    public Optional<RobotR031Task> findLatestByTid(String tid) {
        if (StringUtils.isBlank(tid)) return Optional.empty();
        QueryWrapper<RobotR031Task> qw = new QueryWrapper<>();
        qw.eq("tid", tid).orderByDesc("created_time").last("LIMIT 1");
        return Optional.ofNullable(robotR031TaskMapper.selectOne(qw));
    }

    @Override
    public List<RobotR031Task> findOpen() {
        QueryWrapper<RobotR031Task> qw = new QueryWrapper<>();
        // 未終結：RESULT 為 NULL 或者 不在('END','FAIL','CANCEL')
        // 生成： (external_last_result IS NULL OR external_last_result NOT IN (...))
        qw.isNull("external_last_result")
                .or(wrapper -> wrapper.notIn("external_last_result", TERMINALS))
                .orderByAsc("created_time")
                .orderByAsc("id");
        return robotR031TaskMapper.selectList(qw);
    }

    @Override
    public List<RobotR031Task> findOpenLimited(int limit) {
        QueryWrapper<RobotR031Task> qw = new QueryWrapper<>();
        qw.isNull("external_last_result")
                .or(wrapper -> wrapper.notIn("external_last_result", TERMINALS))
                .orderByAsc("created_time")
                .orderByAsc("id")
                .last("LIMIT " + Math.max(1, limit));
        return robotR031TaskMapper.selectList(qw);
    }

    @Override
    public List<RobotR031Task> findRecentSince(LocalDateTime since, int limit) {
        QueryWrapper<RobotR031Task> qw = new QueryWrapper<>();

        // 只取已終結任務
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

        return robotR031TaskMapper.selectList(qw);
    }

    @Override
    public boolean save(RobotR031Task entity) {
        return robotR031TaskMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(RobotR031Task entity) {
        return robotR031TaskMapper.updateById(entity) > 0;
    }

    @Override
    public boolean updateByLogId(RobotR031Task entity) {
        if (entity == null || entity.getLogId() == null) {
            return false; // 或丟 IllegalArgumentException 視你風格
        }
        UpdateWrapper<RobotR031Task> uw = new UpdateWrapper<>();
        uw.eq("log_id", entity.getLogId());
        // 只會更新 entity 中非 null 欄位（取決於 @TableField(updateStrategy=NOT_NULL) 或全域設定）
        return robotR031TaskMapper.update(entity, uw) > 0;
    }

    @Override
    public boolean updateInboxIdByLogId(Long logId, Long inboxId) {
        if (logId == null || inboxId == null) return false;
        return robotR031TaskMapper.update(
                null,
                new UpdateWrapper<RobotR031Task>()
                        .eq("log_id", logId)
                        .set("inbox_id", inboxId)
                        .set("updated_time", new java.sql.Timestamp(System.currentTimeMillis()))
        ) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return robotR031TaskMapper.deleteById(id) > 0;
    }

    @Override
    public List<RobotR031Task> findAll() {
        return robotR031TaskMapper.selectList(new QueryWrapper<>());
    }
}
