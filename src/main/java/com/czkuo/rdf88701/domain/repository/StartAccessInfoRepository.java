package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.StartAccessInfo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * StartAccessInfo 存取介面（對應資料表：start_access_info）
 *
 * 使用情境（建議流程）：
 *  1) Monitor 偵測到「Start/Reset 請求」(W1035/W1037/W1039/W103B) →
 *     發送 S013 成功後，呼叫 {@link #savePending(String, String, int)} 先入庫一筆 PENDING。
 *  2) S013 ACK Handler 收到回覆（OK/NG + STAFF_LIST） →
 *     呼叫 {@link #updateAckByTid(String, String, String, List, String, LocalDateTime)}
 *     用 TID 回寫結果，並把 writeback_status 設為 WAITING，交由 Writer 寫回 PLC。
 *  3) PLC Writer 週期性撈 {@link #pickWaitingWriteback(int)} →
 *     依照 ACK 結果把 ReturnCode (W0035/W0037/W0039/W003B) 寫 1(OK)/2(NG)，再補握手 →
 *     寫成功呼叫 {@link #markWritebackSuccess(Long, LocalDateTime)}；
 *     寫失敗呼叫 {@link #markWritebackFailed(Long, String)}（可啟動重試策略）。
 *  4) 逾時保護（可選）：排程呼叫 {@link #markTimeoutAsNg(long, String)}，
 *     將久未回的 PENDING 標記 TIMEOUT/NG，並交由 Writer 寫回 PLC。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface StartAccessInfoRepository {

    /* =========================
       共用狀態常數（避免魔法字串）
       ========================= */

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
    Optional<StartAccessInfo> findById(Long id);

    /** 新增一筆（一般情境較少直接呼叫，建議用 {@link #savePending(String, String, int)}）。 */
    boolean save(StartAccessInfo entity);

    /** 以主鍵更新（一般情境較少直接呼叫，建議用專用方法）。 */
    boolean update(StartAccessInfo entity);

    /** 以主鍵刪除。 */
    boolean deleteById(Long id);

    /** 查詢全部（除非管理後台，不建議在熱路徑大量呼叫）。 */
    List<StartAccessInfo> findAll();

    /* =========================
       監控 / ACK / Writer 專用
       ========================= */

    /**
     * 發 S013 成功後，先入庫一筆 PENDING。
     *
     * @param tid         S013 的 TID（yyyyMMddHHmmssSSS，冪等鍵）
     * @param targetCode  啟動對象：WIP / ZIPA / ZIPB / FSK6001A
     * @param reqValue    請求值：1=START，256=RESET
     * @return 是否成功插入
     */
    boolean savePending(String tid, String targetCode, int reqValue);

    /**
     * 依 TID 查詢單筆（送出後或 ACK/Writer 流程常用）。
     *
     * @param tid 冪等鍵
     * @return 查到則回傳 Optional.of(entity)，否則 Optional.empty()
     */
    Optional<StartAccessInfo> findByTid(String tid);

    /**
     * 收到 S013 ACK 後，用 TID 更新 ACK 結果與狀態。
     * 同時將 writeback_status 設為 WAITING，交由 Writer 寫 PLC。
     *
     * @param tid            冪等鍵（對應請求）
     * @param ackResult      "OK" / "NG"
     * @param ackMessage     補充說明（可為空）
     * @param staffList      人員工號清單（可為 null）
     * @param statusAfterAck 狀態：{@link #STATUS_ACK_OK} 或 {@link #STATUS_ACK_NG}
     * @param ackAt          收到 ACK 的時間
     * @return 是否更新成功
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
    List<StartAccessInfo> pickWaitingWriteback(int limit);

    /**
     * PLC Writer 寫成功後標記成功（會自動累加 attempts，清空錯誤並填寫 written_at）。
     *
     * @param id         資料列主鍵
     * @param writtenAt  寫 PLC 的完成時間
     * @return 是否更新成功
     */
    boolean markWritebackSuccess(Long id, LocalDateTime writtenAt);

    /**
     * PLC Writer 寫失敗後標記失敗（會自動累加 attempts，保留最後錯誤訊息）。
     *
     * @param id    資料列主鍵
     * @param error 失敗原因（最後一次）
     * @return 是否更新成功
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
