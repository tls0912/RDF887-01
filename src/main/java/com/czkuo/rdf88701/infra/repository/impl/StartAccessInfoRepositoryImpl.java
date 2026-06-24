package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.czkuo.rdf88701.domain.repository.StartAccessInfoRepository;
import com.czkuo.rdf88701.infra.entity.StartAccessInfo;
import com.czkuo.rdf88701.infra.mapper.StartAccessInfoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * StartAccessInfoRepository 實作
 *
 * 功能涵蓋：
 *  - 基本 CRUD
 *  - S013 流程專用方法（savePending / updateAckByTid / pickWaitingWriteback / markWritebackXxx / markTimeoutAsNg）
 *
 * 注意：
 *  - staff_list 以 JSON 存字串，這裡用 ObjectMapper 做序列化。
 *  - req_value 會有 1(START)、256(RESET)；請確保 DB 欄位與 Entity 型別支援 256。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
public class StartAccessInfoRepositoryImpl implements StartAccessInfoRepository {

    private final StartAccessInfoMapper startAccessInfoMapper;
    private final ObjectMapper objectMapper;

    public StartAccessInfoRepositoryImpl(StartAccessInfoMapper startAccessInfoMapper,
                                         ObjectMapper objectMapper) {
        this.startAccessInfoMapper = startAccessInfoMapper;
        this.objectMapper = objectMapper;
    }

    /* ============== 基本 CRUD ============== */

    @Override
    public Optional<StartAccessInfo> findById(Long id) {
        return Optional.ofNullable(startAccessInfoMapper.selectById(id));
    }

    @Override
    public boolean save(StartAccessInfo entity) {
        return startAccessInfoMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(StartAccessInfo entity) {
        return startAccessInfoMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return startAccessInfoMapper.deleteById(id) > 0;
    }

    @Override
    public List<StartAccessInfo> findAll() {
        return startAccessInfoMapper.selectList(new QueryWrapper<>());
    }

    /* ======== 監控 / ACK / Writer 專用 ======== */

    /**
     * 發 S013 成功後，先入庫一筆 PENDING。
     * 預設 writeback_status=WAITING，讓 Writer 稍後把結果寫回 PLC。
     *
     * ⚠ 若你的 Entity 的 reqValue 是 Byte，將無法存 256。
     *   建議把 Entity 欄位改為 Integer/Short，DB 用 SMALLINT/INT。
     */
    @Override
    public boolean savePending(String tid, String targetCode, int reqValue) {
        StartAccessInfo row = new StartAccessInfo();
        row.setTid(tid);
        row.setTargetCode(targetCode);

        row.setReqValue((short) reqValue);

        row.setStatus(STATUS_PENDING);
        row.setAckResult(null);
        row.setAckMessage(null);
        row.setStaffList(null);
        row.setAckAt(null);

        row.setRetries(0);
        row.setLastError(null);

        row.setWritebackStatus(WB_WAITING);
        row.setWritebackAttempts(0);
        row.setWritebackError(null);
        row.setWrittenAt(null);
        // created_at / updated_at 交由 DB default

        return startAccessInfoMapper.insert(row) > 0;
    }

    /** 依 TID 查單筆（送出後或 ACK/Writer 流程常用）。 */
    @Override
    public Optional<StartAccessInfo> findByTid(String tid) {
        StartAccessInfo one = startAccessInfoMapper.selectOne(
                new QueryWrapper<StartAccessInfo>()
                        .eq("tid", tid)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(one);
    }

    /**
     * 收到 S013 ACK 後，用 TID 更新 ACK 結果與狀態，並把 writeback_status 設為 WAITING。
     * 交給 PLC Writer 寫回 ReturnCode（OK=1 / NG=2）與握手。
     */
    @Override
    public boolean updateAckByTid(String tid,
                                  String ackResult,
                                  String ackMessage,
                                  List<String> staffList,
                                  String statusAfterAck,
                                  LocalDateTime ackAt) {
        String staffJson = null;
        try {
            if (staffList != null) {
                staffJson = objectMapper.writeValueAsString(staffList);
            }
        } catch (Exception e) {
            // JSON 失敗不應中斷流程，可退回 null；若需更嚴格可改為拋出/記錄
            staffJson = null;
        }

        UpdateWrapper<StartAccessInfo> uw = new UpdateWrapper<>();
        uw.eq("tid", tid)
                .set("ack_result", ackResult)
                .set("ack_message", ackMessage)
                .set("staff_list", staffJson)
                .set("ack_at", ackAt)
                .set("status", statusAfterAck)
                .set("writeback_status", WB_WAITING)
                .set("last_error", null)
                .set("writeback_error", null);

        return startAccessInfoMapper.update(null, uw) > 0;
    }

    /**
     * 讓 PLC Writer 撈取待寫回的資料列（writeback_status=WAITING）。
     * 依建立時間排序，先來先處理。
     */
    @Override
    public List<StartAccessInfo> pickWaitingWriteback(int limit) {
        return startAccessInfoMapper.selectList(
                new QueryWrapper<StartAccessInfo>()
                        .eq("writeback_status", WB_WAITING)
                        .orderByAsc("created_at")
                        .last("LIMIT " + Math.max(limit, 1))
        );
    }

    /**
     * PLC Writer 寫成功後標記成功：
     * - writeback_status = WRITTEN
     * - writeback_attempts += 1
     * - 清空 last_error / writeback_error
     * - written_at = 參數
     */
    @Override
    public boolean markWritebackSuccess(Long id, LocalDateTime writtenAt) {
        UpdateWrapper<StartAccessInfo> uw = new UpdateWrapper<>();
        uw.eq("id", id)
                .set("writeback_status", WB_WRITTEN)
                .set("written_at", writtenAt)
                .set("last_error", null)
                .set("writeback_error", null)
                // attempts 自增
                .setSql("writeback_attempts = writeback_attempts + 1");
        return startAccessInfoMapper.update(null, uw) > 0;
    }

    /**
     * PLC Writer 寫失敗後標記失敗：
     * - writeback_status = FAILED
     * - writeback_error = 最後錯誤
     * - writeback_attempts += 1
     */
    @Override
    public boolean markWritebackFailed(Long id, String error) {
        UpdateWrapper<StartAccessInfo> uw = new UpdateWrapper<>();
        uw.eq("id", id)
                .set("writeback_status", WB_FAILED)
                .set("writeback_error", error)
                .setSql("writeback_attempts = writeback_attempts + 1");
        return startAccessInfoMapper.update(null, uw) > 0;
    }

    /**
     * 將超過一定時間仍為 PENDING 的資料標記逾時（TIMEOUT）並視同 NG，
     * 並讓其 writeback_status=WAITING，交由 Writer 寫回 PLC。
     */
    @Override
    public int markTimeoutAsNg(long olderThanMillis, String timeoutMsg) {
        // 以 created_at 辨識逾時；條件：status=PENDING 且 created_at < cutoff
        LocalDateTime cutoff = LocalDateTime.now().minusNanos(olderThanMillis * 1_000_000L);
        LocalDateTime now = LocalDateTime.now();

        UpdateWrapper<StartAccessInfo> uw = new UpdateWrapper<>();
        uw.eq("status", STATUS_PENDING)
                .lt("created_at", cutoff)
                .set("status", STATUS_TIMEOUT)
                .set("ack_result", ACK_NG)
                .set("ack_message", timeoutMsg)
                .set("ack_at", now)
                .set("last_error", timeoutMsg)
                .set("writeback_status", WB_WAITING);
        return startAccessInfoMapper.update(null, uw);
    }
}
