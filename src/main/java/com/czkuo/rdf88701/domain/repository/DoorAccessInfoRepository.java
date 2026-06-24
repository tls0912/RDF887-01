package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.DoorAccessInfo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * DoorAccessInfo 存取介面
 *
 * 使用情境（建議流程）：
 *  1) Monitor 偵測到開/關門請求 → 發 S011/S012 成功後，呼叫 {@link #savePending(String, int, int)} 先入庫一筆 PENDING
 *  2) S011/S012 ACK Handler 收到回覆 → 呼叫 {@link #updateAckByTid(String, String, String, List, String, LocalDateTime)}
 *     將 ACK 結果（OK/NG、說明與人員清單）更新回同一筆（靠 TID）
 *  3) PLC Writer 週期撈 {@link #pickWaitingWriteback(int)} → 依 ACK 結果寫 PLC → 寫成功呼叫
 *     {@link #markWritebackSuccess(Long, LocalDateTime)}，失敗則呼叫 {@link #markWritebackFailed(Long, String)}
 *  4) 逾時防護（可選）排程：呼叫 {@link #markTimeoutAsNg(long, String)}，將久未回的 PENDING 標記 TIMEOUT/NG，
 *     也交給 PLC Writer 回寫
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface DoorAccessInfoRepository {

    /* ==========================
       建議共用的狀態常數（避免魔法字串）
       ========================== */
    // status
    String STATUS_PENDING   = "PENDING";
    String STATUS_ACK_OK    = "ACK_OK";
    String STATUS_ACK_NG    = "ACK_NG";
    String STATUS_TIMEOUT   = "TIMEOUT";
    String STATUS_CANCELLED = "CANCELLED";

    // ack_result
    String ACK_OK = "OK";
    String ACK_NG = "NG";

    // writeback_status
    String WB_WAITING = "WAITING";
    String WB_WRITTEN = "WRITTEN";
    String WB_FAILED  = "FAILED";

    /* ==============
       基本 CRUD
       ============== */

    /** 依主鍵查單筆。 */
    Optional<DoorAccessInfo> findById(Long id);

    /** 新增一筆（一般情境下可少用；多用 savePending）。 */
    boolean save(DoorAccessInfo entity);

    /** 以主鍵更新（一般情境下可少用；多用專用的 updateAckByTid / markWritebackXxx）。 */
    boolean update(DoorAccessInfo entity);

    /** 以主鍵刪除。 */
    boolean deleteById(Long id);

    /** 查詢全部（除非管理後台，不建議在熱路徑大量呼叫）。 */
    List<DoorAccessInfo> findAll();

    /* =========================
       監控/ACK/Writer 專用方法
       ========================= */

    /**
     * 送出 S011/S012 成功後，先入庫一筆 PENDING。
     *
     * @param tid      對應 MQTT 訊息的 TID（冪等鍵）
     * @param doorNo   門號（1..N）
     * @param reqValue 請求值：1=OPEN、2=CLOSE
     * @return 是否成功插入
     */
    boolean savePending(String tid, int doorNo, int reqValue);

    /**
     * 依 TID 查詢單筆。送出後或 ACK/Writer 流程常用。
     */
    Optional<DoorAccessInfo> findByTid(String tid);

    /**
     * 收到 ACK 後，用 TID 更新 ACK 結果與狀態。
     *
     * @param tid            冪等鍵（對應請求）
     * @param ackResult      "OK" / "NG"
     * @param ackMessage     補充說明（可為空）
     * @param staffList      人員工號清單（可為 null）
     * @param statusAfterAck 狀態：ACK_OK 或 ACK_NG
     * @param ackAt          收到 ACK 的時間
     * @return 是否有更新到資料
     */
    boolean updateAckByTid(String tid,
                           String ackResult,
                           String ackMessage,
                           List<String> staffList,
                           String statusAfterAck,
                           LocalDateTime ackAt);

    /**
     * 讓 PLC Writer 撈取待寫回的資料列（writeback_status=WAITING）。
     * 建議依建立時間排序，先來先寫。
     *
     * @param limit 每次最多撈幾筆
     * @return 等待寫回的清單
     */
    List<DoorAccessInfo> pickWaitingWriteback(int limit);

    /**
     * PLC Writer 寫成功後標記成功。
     *
     * @param id       資料列主鍵
     * @param writtenAt 寫 PLC 的完成時間
     * @return 是否成功更新
     */
    boolean markWritebackSuccess(Long id, LocalDateTime writtenAt);

    /**
     * PLC Writer 寫失敗後標記失敗（可配合重試策略）。
     *
     * @param id    資料列主鍵
     * @param error 失敗原因（最後一次）
     * @return 是否成功更新
     */
    boolean markWritebackFailed(Long id, String error);

    /**
     * 將超過一定時間仍為 PENDING 的資料標記逾時（TIMEOUT）並視同 NG，
     * 同時讓其 writeback_status=WAITING，交由 Writer 將 NG 寫回 PLC。
     *
     * @param olderThanMillis 早於「現在 - 此毫秒數」的 PENDING 視為逾時
     * @param timeoutMsg      逾時訊息（記錄在 ack_message/last_error）
     * @return 被更新的筆數
     */
    int markTimeoutAsNg(long olderThanMillis, String timeoutMsg);
}
