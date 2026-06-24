package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.S072Session;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface S072SessionRepository {

    Optional<S072Session> findById(Long id);

    boolean save(S072Session entity);

    boolean update(S072Session entity);

    boolean deleteById(Long id);

    List<S072Session> findAll();

    /** 找「現役」會話：carrierId 且狀態非 CLOSED */
    Optional<S072Session> findActiveByCarrierId(String carrierId);

    /** 依 TID 取單筆（用於 ACK 回填或除錯） */
    Optional<S072Session> findByTid(String tid);

    /** 更新第一次拍照結果（路徑+時間） */
    boolean updateFirstCapture(Long id, String imagePath1, LocalDateTime capturedAt1);

    /** 更新第二次拍照結果（路徑+時間） */
    boolean updateSecondCapture(Long id, String imagePath2, LocalDateTime capturedAt2);

    /** 設定/更新 TID（送出 S072 之後寫回 session） */
    boolean updateTid(Long id, String tid);

    /** 單純更新狀態（by id） */
    boolean updateStatus(Long id, String status);

    /** 送出 S072 後，同步更新 tid 與狀態（例如 SENT） */
    boolean updateTidAndStatus(Long id, String tid, String status);

    /** 單純更新狀態（by tid） */
    boolean updateStatusByTid(String tid, String status);

    /** 回填 ACK 結果（同時狀態切到 ACK） */
    boolean markAck(Long id, String result, String resultMessage);

    /** 回填 ACK（by tid） */
    boolean markAckByTid(String tid, String result, String resultMessage);

    /** 標記錯誤（同時將狀態切到 ERROR） */
    boolean markError(Long id, String errorMessage);

    /** 標記錯誤（by tid） */
    boolean markErrorByTid(String tid, String errorMessage);

    /** 標記結束（CLOSED） */
    boolean close(Long id);

    /** 取指定狀態的清單（例如批次送 S072 或清理） */
    List<S072Session> findByStatus(String status, int limit);

    /** 關閉該載具所有「非 CLOSED」的會話，回傳受影響筆數 */
    int closeAllActiveByCarrierId(String carrierId);
}
