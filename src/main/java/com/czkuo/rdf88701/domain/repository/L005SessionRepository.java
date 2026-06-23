package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.L005Session;
import java.util.List;
import java.util.Optional;

public interface L005SessionRepository {

    Optional<L005Session> findById(Long id);

    boolean save(L005Session entity);

    boolean update(L005Session entity);

    boolean deleteById(Long id);

    List<L005Session> findAll();

    // ====== 常用查詢 ======

    /** 依 TID 取單筆 */
    Optional<L005Session> findByTid(String tid);

    /** 取某條碼目前現役 session（is_valid=1），若多筆以時間/自增ID靠後者為準 */
    Optional<L005Session> findActiveByBarcode(String barcode);

    /** 只取目前現役的 TID（沿用同 TID 回報時用） */
    Optional<String> findCurrentTidByBarcode(String barcode);

    /** 取某條碼的歷史（新到舊） */
    List<L005Session> findByBarcode(String barcode);

    /** 取某條碼最近 N 筆歷史（新到舊） */
    List<L005Session> findRecentByBarcode(String barcode, int limit);

    /** 依對方回填的載具 ID（peer_carrier_id）取最近一筆（新到舊） */
    Optional<L005Session> findLatestByPeerCarrierId(String carrierId);

    // ====== 狀態更新 ======

    /** 將同條碼的現役設為失效（被 newTid 取代） */
    boolean invalidateAllActiveByBarcode(String barcode, String newTid);

    /** 更新對方 ACK 結果（peer_* 欄位） */
    boolean updatePeerAckByTid(
            String tid,
            String result, String resultMsg,
            String carrierId, String lotId,
            String trayHigh, String trayType, String msgType,
            String payloadJson
    );

    /** 更新我方內部狀態（INIT→SENT→ACKED→COMPLETED/FAILED） */
    boolean updateInternalStateByTid(String tid, String internalState, String failReason);

    /** 更新對外結果（OK/START/END/FAIL/CANCEL） */
    boolean updateExternalResultByTid(String tid, String result, String reason);
}
