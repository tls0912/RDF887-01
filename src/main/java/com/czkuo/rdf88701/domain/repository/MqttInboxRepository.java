package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.MqttInbox;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface MqttInboxRepository {

    // ===== 基本 CRUD =====

    Optional<MqttInbox> findById(Long id);

    boolean save(MqttInbox entity);

    boolean update(MqttInbox entity);

    boolean deleteById(Long id);

    List<MqttInbox> findAll();

    /**
     * （UI/監控用）查詢仍未結案的 S074 觀測範圍。
     * 保留舊接口以免破壞相依；建議改用 findOpenForR029。
     */
    @Deprecated
    List<MqttInbox> findOpenForS074();

    /** 依 TID 取最新一筆（容忍歷史重覆 TID） */
    Optional<MqttInbox> findLatestByTid(String tid);

    /** 依 CMD_ID + TID 取最新一筆（精準；容忍歷史重覆） */
    Optional<MqttInbox> findLatestByCmdIdAndTid(String cmdId, String tid);

    // ===== 佇列操作 =====

    /**
     * 將入站 COMMAND 匯入佇列（RECEIVED）
     *
     * @return 佇列 id（mqtt_inbox.id）；若 logId 已存在則回傳既有 id（依 UNIQUE KEY(log_id) 防重）
     */
    Long enqueueFromInbound(Long logId, String tid, String cmdId,
                            String sender, String receiver, String topic,
                            LocalDateTime recvTime, int priority);

    /**
     * 取出一筆可處理的佇列並「原子佔鎖」為 IN_PROGRESS。
     * - 適用一般用途（不過濾指令別）
     * - 排序：priority ASC, next_attempt_time ASC, recv_time ASC
     *
     * @param workerId 佔鎖者（節點/執行緒名稱）
     * @param lockTtl  鎖存活時間（例如 Duration.ofMinutes(2)）
     */
    Optional<MqttInbox> pickOneForProcessing(String workerId, Duration lockTtl);

    /**
     * 只挑指定 cmdId 的一筆佇列並「原子佔鎖」為 IN_PROGRESS。
     * - 典型用法：只處理 R029（cmdId="R029"）
     * - 排序：priority ASC, next_attempt_time ASC, recv_time ASC
     *
     * @param cmdId    指令別（例：R029）
     * @param workerId 佔鎖者（節點/執行緒名稱）
     * @param lockTtl  鎖存活時間
     */
    Optional<MqttInbox> pickOneForProcessingByCmd(String cmdId, String workerId, Duration lockTtl);
    Optional<MqttInbox> pickOneForProcessingByCmdNoNextAttemptTime(String cmdId, String workerId, Duration lockTtl);

    /**
     * 調整佇列優先權（1 高 → 9 低；會自動 clamp 到 [1,9]）
     * - 用途：一旦某 R029 已成功派出第一顆，即將該 inbox priority 調為 1，
     *         使其在「全部 LOT 派完/到位」前持續被優先撿取（黏著度）。
     */
    boolean updatePriority(Long id, int priority);

    /** 標記為 QUEUED（釋放鎖） */
    boolean markQueued(Long id);

    /** 標記為 DONE 並回填對應的內部任務資訊 */
    boolean markDone(Long id, String mappedTaskType, Long mappedTaskId);

    /** 標記為 REJECTED（附加原因到 process_errors） */
    boolean markRejected(Long id, String reason);

    /** 標記為 CANCELLED（附加原因到 process_errors） */
    boolean markCancelled(Long id, String reason);

    /**
     * 釋放逾時鎖：
     * - 條件：process_state='IN_PROGRESS' 且 lock_until < NOW()
     * - 動作：轉回 QUEUED & next_attempt_time=NOW()
     *
     * @return 影響筆數
     */
    int releaseExpiredLocks();

    /**
     * 退避重排：回 QUEUED 並設定 next_attempt_time = now + backoff
     * - 建議 backoff 1~3 秒：可避免飢餓又能快速回到該筆
     */
    boolean requeue(Long id, Duration backoff);
}
