package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.czkuo.rdf88701.domain.repository.RobotR008TaskRepository;
import com.czkuo.rdf88701.infra.entity.RobotR008Task;
import com.czkuo.rdf88701.infra.mapper.RobotR008TaskMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class RobotR008TaskRepositoryImpl implements RobotR008TaskRepository {

    private final RobotR008TaskMapper robotR008TaskMapper;

    private static final List<String> TERMINALS = Arrays.asList("END", "FAIL", "CANCEL");

    public RobotR008TaskRepositoryImpl(RobotR008TaskMapper robotR008TaskMapper) {
        this.robotR008TaskMapper = robotR008TaskMapper;
    }

    @Override
    public Optional<RobotR008Task> findById(Long id) {
        return Optional.ofNullable(robotR008TaskMapper.selectById(id));
    }

    @Override
    public Optional<RobotR008Task> findByLogId(Long logId) {
        if (logId == null) return Optional.empty();
        QueryWrapper<RobotR008Task> qw = new QueryWrapper<>();
        qw.eq("log_id", logId).last("LIMIT 1");
        return Optional.ofNullable(robotR008TaskMapper.selectOne(qw));
    }

    @Override
    public Optional<RobotR008Task> findLatestByTid(String tid) {
        if (StringUtils.isBlank(tid)) return Optional.empty();
        QueryWrapper<RobotR008Task> qw = new QueryWrapper<>();
        qw.eq("tid", tid).orderByDesc("created_time").last("LIMIT 1");
        return Optional.ofNullable(robotR008TaskMapper.selectOne(qw));
    }

    @Override
    public List<String> findBinTypeByCarrierId(String carrierId) {
        List<String> ids = robotR008TaskMapper.selectBinTypeByCarrierId(carrierId);
        return ids != null ? ids : Collections.emptyList();
    }
    @Override
    public List<RobotR008Task> findOpen() {
        QueryWrapper<RobotR008Task> qw = new QueryWrapper<>();
        // 未終結：RESULT 為 NULL 或者 不在('END','FAIL','CANCEL')
        // 生成： (external_last_result IS NULL OR external_last_result NOT IN (...))
        qw.isNull("external_last_result")
                .or(wrapper -> wrapper.notIn("external_last_result", TERMINALS))
                .orderByAsc("created_time")
                .orderByAsc("id");
        return robotR008TaskMapper.selectList(qw);
    }

    @Override
    public List<RobotR008Task> findOpenLimited(int limit) {
        QueryWrapper<RobotR008Task> qw = new QueryWrapper<>();
        qw.isNull("external_last_result")
                .or(wrapper -> wrapper.notIn("external_last_result", TERMINALS))
                .orderByAsc("created_time")
                .orderByAsc("id")
                .last("LIMIT " + Math.max(1, limit));
        return robotR008TaskMapper.selectList(qw);
    }

    @Override
    public List<RobotR008Task> findRecentSince(LocalDateTime since, int limit) {
        QueryWrapper<RobotR008Task> qw = new QueryWrapper<>();

        // 只取已終結的任務
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

        return robotR008TaskMapper.selectList(qw);
    }

    @Override
    public boolean save(RobotR008Task entity) {
        return robotR008TaskMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(RobotR008Task entity) {
        return robotR008TaskMapper.updateById(entity) > 0;
    }

    @Override
    public boolean updateByLogId(RobotR008Task entity) {
        if (entity == null || entity.getLogId() == null) {
            return false; // 或丟 IllegalArgumentException 視你風格
        }
        UpdateWrapper<RobotR008Task> uw = new UpdateWrapper<>();
        uw.eq("log_id", entity.getLogId());
        // 只會更新 entity 中非 null 欄位（取決於 @TableField(updateStrategy=NOT_NULL) 或全域設定）
        return robotR008TaskMapper.update(entity, uw) > 0;
    }

    @Override
    public boolean updateInboxIdByLogId(Long logId, Long inboxId) {
        if (logId == null || inboxId == null) return false;
        return robotR008TaskMapper.update(
                null,
                new UpdateWrapper<RobotR008Task>()
                        .eq("log_id", logId)
                        .set("inbox_id", inboxId)
                        .set("updated_time", new java.sql.Timestamp(System.currentTimeMillis()))
        ) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return robotR008TaskMapper.deleteById(id) > 0;
    }

    @Override
    public List<RobotR008Task> findAll() {
        return robotR008TaskMapper.selectList(new QueryWrapper<>());
    }
}
