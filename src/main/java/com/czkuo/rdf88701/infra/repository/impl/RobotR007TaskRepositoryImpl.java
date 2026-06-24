package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.czkuo.rdf88701.domain.repository.RobotR007TaskRepository;
import com.czkuo.rdf88701.infra.entity.RobotR007Task;
import com.czkuo.rdf88701.infra.mapper.RobotR007TaskMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
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
public class RobotR007TaskRepositoryImpl implements RobotR007TaskRepository {

    private final RobotR007TaskMapper robotR007TaskMapper;

    private static final List<String> TERMINALS = Arrays.asList("END", "FAIL", "CANCEL");

    public RobotR007TaskRepositoryImpl(RobotR007TaskMapper robotR007TaskMapper) {
        this.robotR007TaskMapper = robotR007TaskMapper;
    }

    @Override
    public Optional<RobotR007Task> findById(Long id) {
        return Optional.ofNullable(robotR007TaskMapper.selectById(id));
    }

    @Override
    public Optional<RobotR007Task> findByLogId(Long logId) {
        if (logId == null) return Optional.empty();
        QueryWrapper<RobotR007Task> qw = new QueryWrapper<>();
        qw.eq("log_id", logId).last("LIMIT 1");
        return Optional.ofNullable(robotR007TaskMapper.selectOne(qw));
    }

    @Override
    public Optional<RobotR007Task> findLatestByTid(String tid) {
        if (StringUtils.isBlank(tid)) return Optional.empty();
        QueryWrapper<RobotR007Task> qw = new QueryWrapper<>();
        qw.eq("tid", tid).orderByDesc("created_time").last("LIMIT 1");
        return Optional.ofNullable(robotR007TaskMapper.selectOne(qw));
    }

    @Override
    public Optional<RobotR007Task> findByAmrTid(String amrTid) {
        if (amrTid == null || amrTid.isBlank()) return Optional.empty();
        QueryWrapper<RobotR007Task> qw = new QueryWrapper<>();
        qw.eq("amr_tid", amrTid).last("LIMIT 1");
        return Optional.ofNullable(robotR007TaskMapper.selectOne(qw));
    }

    @Override
    public Optional<RobotR007Task> findLatestByCarrierId(String carrierId) {
        if (carrierId == null || carrierId.isBlank()) return Optional.empty();
        QueryWrapper<RobotR007Task> qw = new QueryWrapper<>();
        qw.eq("carrier_id", carrierId)
                .orderByDesc("updated_time")
                .orderByDesc("id")
                .last("LIMIT 1");
        return Optional.ofNullable(robotR007TaskMapper.selectOne(qw));
    }

    @Override
    public List<RobotR007Task> findOpen() {
        QueryWrapper<RobotR007Task> qw = new QueryWrapper<>();
        // 未終結：RESULT 為 NULL 或者 不在('END','FAIL','CANCEL')
        // 生成： (external_last_result IS NULL OR external_last_result NOT IN (...))
        qw.isNull("external_last_result")
                .or(wrapper -> wrapper.notIn("external_last_result", TERMINALS))
                .orderByAsc("created_time")
                .orderByAsc("id");
        return robotR007TaskMapper.selectList(qw);
    }

    @Override
    public List<RobotR007Task> findOpenLimited(int limit) {
        QueryWrapper<RobotR007Task> qw = new QueryWrapper<>();
        qw.isNull("external_last_result")
                .or(wrapper -> wrapper.notIn("external_last_result", TERMINALS))
                .orderByAsc("created_time")
                .orderByAsc("id")
                .last("LIMIT " + Math.max(1, limit));
        return robotR007TaskMapper.selectList(qw);
    }

    @Override
    public Optional<RobotR007Task> findLatestOpenWithStkPortByDestLoc(String destLoc) {
        if (StringUtils.isBlank(destLoc)) return Optional.empty();

        QueryWrapper<RobotR007Task> qw = new QueryWrapper<>();
        // 未終結
        qw.and(w -> w.isNull("external_last_result")
                .or()
                .notIn("external_last_result", TERMINALS));

        // 同 dest_loc + stk_port 非空
        qw.eq("dest_loc", destLoc)
                .isNotNull("stk_port")
                .ne("stk_port", "")
                .orderByDesc("updated_time")
                .orderByDesc("id")
                .last("LIMIT 1");

        return Optional.ofNullable(robotR007TaskMapper.selectOne(qw));
    }

    @Override
    public List<RobotR007Task> findRecentSince(LocalDateTime since, int limit) {
        QueryWrapper<RobotR007Task> qw = new QueryWrapper<>();

        // 只取已終結任務
        qw.in("external_last_result", TERMINALS);

        // 如果有指定 since，就用 created_time / external_last_time 其中一個 >= since
        if (since != null) {
            Timestamp ts = Timestamp.valueOf(since);
            qw.and(w -> w.ge("created_time", ts)
                    .or()
                    .ge("external_last_time", ts));
        }

        qw.orderByDesc("created_time")
                .orderByDesc("id")
                .last("LIMIT " + Math.max(1, limit));

        return robotR007TaskMapper.selectList(qw);
    }

    @Override
    public boolean save(RobotR007Task entity) {
        return robotR007TaskMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(RobotR007Task entity) {
        return robotR007TaskMapper.updateById(entity) > 0;
    }

    @Override
    public boolean updateByLogId(RobotR007Task entity) {
        if (entity == null || entity.getLogId() == null) return false;
        UpdateWrapper<RobotR007Task> uw = new UpdateWrapper<>();
        uw.eq("log_id", entity.getLogId());
        // 依據全域或欄位的 updateStrategy 設定，只更新非 null 欄位
        return robotR007TaskMapper.update(entity, uw) > 0;
    }

    @Override
    public boolean updateInboxIdByLogId(Long logId, Long inboxId) {
        if (logId == null || inboxId == null) return false;
        return robotR007TaskMapper.update(
                null,
                new UpdateWrapper<RobotR007Task>()
                        .eq("log_id", logId)
                        .set("inbox_id", inboxId)
                        .set("updated_time", new java.sql.Timestamp(System.currentTimeMillis()))
        ) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return robotR007TaskMapper.deleteById(id) > 0;
    }

    @Override
    public List<RobotR007Task> findAll() {
        return robotR007TaskMapper.selectList(new QueryWrapper<>());
    }

    // ========== ZIP 派單追蹤 ==========

    @Override
    public boolean zipMarkAttempt(Long logId, String zipRequestJson) {
        if (logId == null) return false;
        return robotR007TaskMapper.update(
                null,
                new UpdateWrapper<RobotR007Task>()
                        .eq("log_id", logId)
                        .setSql("zip_attempts = COALESCE(zip_attempts,0) + 1")
                        .set("zip_last_attempt_time", nowTs())
                        .set("zip_request_json", zipRequestJson)
                        .set("updated_time", nowTs())
        ) > 0;
    }

    @Override
    public boolean zipMarkAccepted(Long logId, String zipResponseJson, String message) {
        if (logId == null) return false;
        return robotR007TaskMapper.update(
                null,
                new UpdateWrapper<RobotR007Task>()
                        .eq("log_id", logId)
                        .set("zip_state", "ACCEPTED")
                        .set("zip_accept_time", nowTs())
                        .set("zip_result_code", "0")
                        .set("zip_result_message", message)
                        .set("zip_response_json", zipResponseJson)
                        .set("updated_time", nowTs())
        ) > 0;
    }

    @Override
    public boolean zipMarkRejected(Long logId, String code, String message, String zipResponseJson) {
        if (logId == null) return false;
        return robotR007TaskMapper.update(
                null,
                new UpdateWrapper<RobotR007Task>()
                        .eq("log_id", logId)
                        .set("zip_state", "REJECTED")
                        .set("zip_result_code", code)
                        .set("zip_result_message", message)
                        .set("zip_response_json", zipResponseJson)
                        .set("updated_time", nowTs())
        ) > 0;
    }

    @Override
    public boolean zipMarkError(Long logId, String message) {
        if (logId == null) return false;
        return robotR007TaskMapper.update(
                null,
                new UpdateWrapper<RobotR007Task>()
                        .eq("log_id", logId)
                        .set("zip_state", "ERROR")
                        .set("zip_result_message", message)
                        .set("updated_time", nowTs())
        ) > 0;
    }

    // ========== AMR 轉發 / ACK 追蹤 ==========

    @Override
    public boolean amrMarkSent(Long logId, String amrTid, Long forwardLogId, String amrRequestJson) {
        if (logId == null) return false;
        UpdateWrapper<RobotR007Task> uw = new UpdateWrapper<>();
        uw.eq("log_id", logId)
                .set("amr_state", "SENT")
                .setSql("amr_attempts = COALESCE(amr_attempts,0) + 1")
                .set("amr_last_attempt_time", nowTs())
                .set("amr_tid", amrTid)
                .set("amr_forward_log_id", forwardLogId)
                .set("amr_request_json", amrRequestJson)
                .set("updated_time", nowTs());
        return robotR007TaskMapper.update(null, uw) > 0;
    }

    @Override
    public boolean amrMarkAckOkByTid(String amrTid, Long ackLogId, String ackJson) {
        if (amrTid == null || amrTid.isBlank()) return false;
        UpdateWrapper<RobotR007Task> uw = new UpdateWrapper<>();
        uw.eq("amr_tid", amrTid)
                .set("amr_state", "OK")
                .set("amr_last_ack_time", nowTs())
                .set("amr_ack_start_log_id", ackLogId)
                .set("amr_last_ack_json", ackJson)
                .set("updated_time", nowTs());
        return robotR007TaskMapper.update(null, uw) > 0;
    }

    @Override
    public boolean amrMarkAckStartByTid(String amrTid, Long ackLogId, String ackJson) {
        if (amrTid == null || amrTid.isBlank()) return false;
        UpdateWrapper<RobotR007Task> uw = new UpdateWrapper<>();
        uw.eq("amr_tid", amrTid)
                .set("amr_state", "START")
                .set("amr_last_ack_time", nowTs())
                .set("amr_ack_start_log_id", ackLogId)
                .set("amr_last_ack_json", ackJson)
                .set("updated_time", nowTs());
        return robotR007TaskMapper.update(null, uw) > 0;
    }

    @Override
    public boolean amrMarkAckFinalByTid(String amrTid, String finalState, String externalResult,
                                        Long ackLogId, String ackJson, String failReason, String cancelReason) {
        if (amrTid == null || amrTid.isBlank()) return false;

        UpdateWrapper<RobotR007Task> uw = new UpdateWrapper<>();
        uw.eq("amr_tid", amrTid)
                .set("amr_state", finalState)                // 'END' | 'FAIL' | 'CANCEL'
                .set("amr_last_ack_time", nowTs())
                .set("amr_ack_end_log_id", ackLogId)
                .set("amr_last_ack_json", ackJson)
                .set("external_last_result", externalResult) // 'END' | 'FAIL' | 'CANCEL'
                .set("external_last_time", nowTs())
                .set("updated_time", nowTs());

        // 依最終狀態同步 internal_state 與原因
        if ("END".equals(finalState)) {
            uw.set("internal_state", "COMPLETED");
        } else if ("FAIL".equals(finalState)) {
            uw.set("internal_state", "FAILED").set("fail_reason", failReason);
        } else if ("CANCEL".equals(finalState)) {
            uw.set("internal_state", "CANCELLED").set("cancel_reason", cancelReason);
        }
        return robotR007TaskMapper.update(null, uw) > 0;
    }

    // ========== utils ==========

    private static Timestamp nowTs() {
        return new Timestamp(System.currentTimeMillis());
    }
}
