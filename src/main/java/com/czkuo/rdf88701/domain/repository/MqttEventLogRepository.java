package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.MqttEventLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface MqttEventLogRepository {

    /* ===== 基本 CRUD ===== */
    Optional<MqttEventLog> findById(Long id);

    Optional<MqttEventLog> findByTid(String tid);

    /** 建議：在 Service 先把 status=PENDING、retryCount=0、nextAttemptTime=now() 塞好再呼叫 */
    boolean save(MqttEventLog entity);

    boolean update(MqttEventLog entity);

    boolean deleteById(Long id);

    List<MqttEventLog> findAll();

    /* ===== 補償/排程 需要的方法 ===== */

    /**
     * 查出「到期要處理」的事件：
     * status in ('PENDING','RETRYING') AND next_attempt_time <= :now
     * 依 next_attempt_time, id ASC 排序，限制回傳筆數。
     */
    List<MqttEventLog> findDueForSend(LocalDateTime now, int limit);

    /**
     * 查出「等待 ACK 但已逾時」的事件：
     * status = 'SENT' AND require_ack = true AND next_attempt_time <= :now
     * 依 next_attempt_time, id ASC 排序，限制回傳筆數。
     */
    List<MqttEventLog> findWaitingAckOverdue(LocalDateTime now, int limit);

    /**
     * 標記為已送出：設定 send_time 與 next_attempt_time（等待 ACK 的超時計時點）
     * 加上期望狀態（expectStatus）做樂觀鎖，避免重複處理。
     */
    boolean tryMarkSent(Long id,
                        String expectStatus,
                        LocalDateTime sendTime,
                        LocalDateTime nextAttemptTime);

    /**
     * 標記為已 ACK（以 TID 對應）：設 ack_time、result_message、status='ACKED'
     * 若要更嚴謹可改成帶 expectStatus 做 CAS（看你的 ACK 流程是否需要）。
     */
    boolean tryMarkAckedByTid(String tid,
                              LocalDateTime ackTime,
                              String resultMessage);

    /**
     * 逾時進入補償：設 status='RETRYING'、retry_count=nextRetryCount、next_attempt_time=...
     * 以 expectStatus 做 CAS，避免非預期覆蓋。
     */
    boolean tryMarkRetrying(Long id,
                            String expectStatus,
                            int nextRetryCount,
                            LocalDateTime nextAttemptTime,
                            String resultMessage);

    /**
     * 超過最大次數或判斷不再重送：設 status='FAILED'，可附原因。
     */
    boolean tryMarkFailed(Long id,
                          String expectStatus,
                          String resultMessage);

    /**
     * 專門標記逾時：設 status='TIMEOUT'，可附原因（例如 "ack-timeout"）。
     * 以 expectStatus 做 CAS（通常 expectStatus = 'SENT'）。
     */
    boolean tryMarkTimeout(Long id,
                           String expectStatus,
                           String resultMessage);

    /** 單純調整下一次嘗試時間（例如人工延後）。 */
    boolean updateNextAttemptTime(Long id, LocalDateTime nextAttemptTime);

    /** 統計狀態數量（監控/報表用）。 */
    int countByStatus(String status);
}
