package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.RobotR007Task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface RobotR007TaskRepository {

    Optional<RobotR007Task> findById(Long id);

    Optional<RobotR007Task> findByLogId(Long logId);

    Optional<RobotR007Task> findLatestByTid(String tid);

    /** 依 AMR TID 快速反查任務（START/END ACK 會用到） */
    Optional<RobotR007Task> findByAmrTid(String amrTid);

    /** 依 CARRIERID 取最新一筆（updated_time DESC, id DESC） */
    Optional<RobotR007Task> findLatestByCarrierId(String carrierId);

    /** 取所有未終結的任務（external_last_result NOT IN ('END','FAIL','CANCEL') 或為 NULL） */
    List<RobotR007Task> findOpen();

    /** 取未終結任務（可限制筆數），排序：created_time ASC, id ASC */
    List<RobotR007Task> findOpenLimited(int limit);

    /** 找同 dest_loc 的「未終結」且 stk_port 非空的最新任務（排除自己可由呼叫端判斷 logId） */
    Optional<RobotR007Task> findLatestOpenWithStkPortByDestLoc(String destLoc);

    /**
     * 取最近一段時間內「已終結」的任務（歷史用）
     * 條件：
     *   - external_last_result IN ('END','FAIL','CANCEL')
     *   - created_time >= since 或 external_last_time >= since
     * 排序：
     *   - created_time DESC, id DESC
     * limit：最大筆數
     */
    List<RobotR007Task> findRecentSince(LocalDateTime since, int limit);

    boolean save(RobotR007Task entity);

    boolean update(RobotR007Task entity);

    /** 以 log_id 為條件做部份欄位更新（只套用非 null 欄位） */
    boolean updateByLogId(RobotR007Task entity);

    /** 在已建好的任務上回填 inbox_id（通常在入佇列成功後補寫） */
    boolean updateInboxIdByLogId(Long logId, Long inboxId);

    boolean deleteById(Long id);

    List<RobotR007Task> findAll();

    // -------- ZIP 派單追蹤 --------
    /** 記錄 ZIP 送出嘗試（+1、時間、request 摘要） */
    boolean zipMarkAttempt(Long logId, String zipRequestJson);

    /** ZIP 接單成功（Result=0） */
    boolean zipMarkAccepted(Long logId, String zipResponseJson, String message);

    /** ZIP 被拒絕 */
    boolean zipMarkRejected(Long logId, String code, String message, String zipResponseJson);

    /** ZIP 呼叫異常（網路/內部錯） */
    boolean zipMarkError(Long logId, String message);

    // -------- AMR 轉發 / ACK 追蹤 --------
    /** AMR 轉發成功（寫入 amr_state=SENT、amr_tid、forward_log_id、request 摘要） */
    boolean amrMarkSent(Long logId, String amrTid, Long forwardLogId, String amrRequestJson);

    /** 收到 OK（用 amrTid 辨識較穩當） */
    boolean amrMarkAckOkByTid(String amrTid, Long ackLogId, String ackJson);

    /** 收到 START（用 amrTid 辨識較穩當） */
    boolean amrMarkAckStartByTid(String amrTid, Long ackLogId, String ackJson);

    /** 收到 END/FAIL/CANCEL（用 amrTid；finalState: ACK_END/FAILED/CANCELLED；externalResult: END/FAIL/CANCEL） */
    boolean amrMarkAckFinalByTid(String amrTid, String finalState, String externalResult,
                                 Long ackLogId, String ackJson, String failReason, String cancelReason);
}
