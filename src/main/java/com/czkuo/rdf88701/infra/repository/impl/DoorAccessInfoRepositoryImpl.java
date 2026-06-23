package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.czkuo.rdf88701.domain.repository.DoorAccessInfoRepository;
import com.czkuo.rdf88701.infra.entity.DoorAccessInfo;
import com.czkuo.rdf88701.infra.mapper.DoorAccessInfoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * DoorAccessInfoRepository 實作
 *
 * 功能覆蓋：
 *  - 基本 CRUD
 *  - 送出 S011/S012 後入庫 PENDING
 *  - 依 TID 回寫 ACK 結果（OK/NG、說明、人員清單），並標記待寫 PLC
 *  - 由 PLC Writer 撈取待寫回（writeback_status=WAITING）
 *  - 寫 PLC 成功/失敗狀態回寫
 *  - 逾時標記（從 PENDING → TIMEOUT/NG），並交給 Writer 寫回 PLC
 */
@Repository
public class DoorAccessInfoRepositoryImpl implements DoorAccessInfoRepository {

    private final DoorAccessInfoMapper doorAccessInfoMapper;

    // 輕量用於序列化 staff_list（JSON）
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public DoorAccessInfoRepositoryImpl(DoorAccessInfoMapper doorAccessInfoMapper) {
        this.doorAccessInfoMapper = doorAccessInfoMapper;
    }

    /* ============== 基本 CRUD ============== */

    @Override
    public Optional<DoorAccessInfo> findById(Long id) {
        return Optional.ofNullable(doorAccessInfoMapper.selectById(id));
    }

    @Override
    public boolean save(DoorAccessInfo entity) {
        return doorAccessInfoMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(DoorAccessInfo entity) {
        return doorAccessInfoMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return doorAccessInfoMapper.deleteById(id) > 0;
    }

    @Override
    public List<DoorAccessInfo> findAll() {
        return doorAccessInfoMapper.selectList(new QueryWrapper<>());
    }

    /* ============ 監控/ACK/Writer 專用 ============ */

    /**
     * 送出 S011/S012 成功後，先入庫一筆 PENDING。
     * 預設 writeback_status=WAITING，交由 Writer 之後把結果寫回 PLC。
     */
    @Override
    public boolean savePending(String tid, int doorNo, int reqValue) {
        DoorAccessInfo row = new DoorAccessInfo();
        row.setTid(tid);
        row.setDoorNo(doorNo);
        row.setReqValue((byte) reqValue);
        row.setStatus(STATUS_PENDING);
        row.setAckResult(null);
        row.setAckMessage(null);
        row.setStaffList(null); // 先無人員資料
        row.setAckAt(null);
        row.setRetries(0);
        row.setLastError(null);
        row.setWritebackStatus(WB_WAITING);
        row.setWritebackAttempts(0);
        row.setWritebackError(null);
        row.setWrittenAt(null);
        // created_at / updated_at 交由 DB default/trigger
        return doorAccessInfoMapper.insert(row) > 0;
    }

    /**
     * 依 TID 取得單筆。
     */
    @Override
    public Optional<DoorAccessInfo> findByTid(String tid) {
        DoorAccessInfo one = doorAccessInfoMapper.selectOne(
                new QueryWrapper<DoorAccessInfo>()
                        .eq("tid", tid)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(one);
    }

    /**
     * 收到 ACK 後，用 TID 更新 ACK 結果與狀態，並設為待寫 PLC（writeback_status=WAITING）。
     * staff_list 以 JSON 字串寫入（若使用者未配置 TypeHandler）。
     */
    @Override
    public boolean updateAckByTid(String tid,
                                  String ackResult,
                                  String ackMessage,
                                  List<String> staffList,
                                  String statusAfterAck,
                                  LocalDateTime ackAt) {
        // 容錯：staff_list 可為 null 或空陣列
        String staffJson;
        try {
            staffJson = (staffList == null)
                    ? null
                    : MAPPER.writeValueAsString(staffList);
        } catch (Exception e) {
            // 序列化失敗時，避免擋流程，退回存成空陣列
            try {
                staffJson = MAPPER.writeValueAsString(Collections.emptyList());
            } catch (Exception ignored) {
                staffJson = "[]";
            }
        }

        UpdateWrapper<DoorAccessInfo> uw = new UpdateWrapper<DoorAccessInfo>()
                .eq("tid", tid)
                .set("ack_result", ackResult)
                .set("ack_message", emptyToNull(ackMessage))
                .set("staff_list", staffJson)
                .set("ack_at", ackAt)
                .set("status", statusAfterAck)        // ACK_OK 或 ACK_NG
                .set("writeback_status", WB_WAITING)  // 交給 Writer 去寫回 PLC
                .set("last_error", null);             // 清上一次錯誤

        return doorAccessInfoMapper.update(null, uw) > 0;
    }

    /**
     * 讓 Writer 撈取待寫回的資料列（writeback_status=WAITING），先來先處理。
     * 這裡以 created_at ASC 排序並限制筆數。
     *
     * ⚠ 高併發情境可考慮：撈取同時先「鎖定」狀態（例如額外加一個 IN_PROGRESS），以免多實例重複處理。
     */
    @Override
    public List<DoorAccessInfo> pickWaitingWriteback(int limit) {
        int n = (limit <= 0) ? 20 : limit;
        return doorAccessInfoMapper.selectList(
                new QueryWrapper<DoorAccessInfo>()
                        .eq("writeback_status", WB_WAITING)
                        .orderByAsc("created_at")
                        .last("LIMIT " + n)
        );
    }

    /**
     * Writer 寫 PLC 成功：標記 WRITTEN，記錄寫入時間，嘗試次數 +1，清空錯誤訊息。
     */
    @Override
    public boolean markWritebackSuccess(Long id, LocalDateTime writtenAt) {
        UpdateWrapper<DoorAccessInfo> uw = new UpdateWrapper<DoorAccessInfo>()
                .eq("id", id)
                .set("writeback_status", WB_WRITTEN)
                .set("written_at", writtenAt)
                .set("writeback_error", null)
                .setSql("writeback_attempts = writeback_attempts + 1");
        return doorAccessInfoMapper.update(null, uw) > 0;
    }

    /**
     * Writer 寫 PLC 失敗：標記 FAILED，累加嘗試次數並記錄最後錯誤。
     * （之後可由排程把 FAILED 重新改回 WAITING 再試，或在這裡直接保持 FAILED 由人介入）
     */
    @Override
    public boolean markWritebackFailed(Long id, String error) {
        UpdateWrapper<DoorAccessInfo> uw = new UpdateWrapper<DoorAccessInfo>()
                .eq("id", id)
                .set("writeback_status", WB_FAILED)
                .set("writeback_error", truncate(error, 500))
                .setSql("writeback_attempts = writeback_attempts + 1");
        return doorAccessInfoMapper.update(null, uw) > 0;
    }

    /**
     * 將超時的 PENDING 轉成 TIMEOUT/NG，並交由 Writer 回寫（writeback_status=WAITING）。
     * 規則：created_at < now - olderThanMillis 且 status=PENDING。
     *
     * @return 受影響的筆數
     */
    @Override
    public int markTimeoutAsNg(long olderThanMillis, String timeoutMsg) {
        LocalDateTime threshold = LocalDateTime.now().minusNanos(olderThanMillis * 1_000_000L);
        UpdateWrapper<DoorAccessInfo> uw = new UpdateWrapper<DoorAccessInfo>()
                .eq("status", STATUS_PENDING)
                .lt("created_at", threshold)
                .set("status", STATUS_TIMEOUT)
                .set("ack_result", ACK_NG)
                .set("ack_message", truncate(timeoutMsg, 255))
                .set("ack_at", LocalDateTime.now())
                .set("last_error", truncate(timeoutMsg, 500))
                .set("writeback_status", WB_WAITING); // 讓 Writer 將 NG 寫回 PLC
        return doorAccessInfoMapper.update(null, uw);
    }

    /* ============== 小工具 ============== */

    /** 空字串轉 null，避免把空白塞進 DB。 */
    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** 簡單截斷，避免超過欄位長度（DB 層也會保護，但這裡先處理以免例外）。 */
    private static String truncate(String s, int max) {
        if (s == null) return null;
        return (s.length() <= max) ? s : s.substring(0, max);
    }
}
