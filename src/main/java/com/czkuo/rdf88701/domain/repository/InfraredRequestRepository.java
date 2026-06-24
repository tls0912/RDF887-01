package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.InfraredRequest;
import java.util.List;
import java.util.Optional;

/**
 * InfraredRequest 資料存取介面
 * - 提供基本 CRUD 與業務邏輯用查詢方法
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface InfraredRequestRepository {

    /** 依 ID 查詢單筆 */
    Optional<InfraredRequest> findById(Long id);

    /** 儲存新資料 */
    boolean save(InfraredRequest entity);

    /** 更新既有資料 */
    boolean update(InfraredRequest entity);

    /** 根據 ID 刪除資料 */
    boolean deleteById(Long id);

    /** 查詢所有資料 */
    List<InfraredRequest> findAll();

    /** 根據 requestKey 查詢（建立時重複檢查或升級用） */
    Optional<InfraredRequest> findByRequestKey(String requestKey);

    /** 判斷 requestKey 是否已存在（建立前檢查） */
    boolean existsByRequestKey(String requestKey);

    /**
     * 判斷指定 Infrared 是否有「尚未完成/未被接受」的請求（裝置忙碌檢查）
     * 建議實作條件：accepted = 'N' 或其他你系統定義的未完成狀態。
     */
    boolean existsUnfinishedRequestForInfrared(Long infraredId);

    /** 查詢所有尚未被接受（accepted = 'N'）的請求（Monitor 掃描用） */
    List<InfraredRequest> findUnacceptedRequests();

    /** 取得某 Infrared 最早一筆尚未接受的請求（依建立時間排序） */
    Optional<InfraredRequest> findFirstUnacceptedByInfraredId(Long infraredId);

    // ===== 新增：建立量測請求 =====

    /**
     * 建立一筆「MEASURE」請求（帶 container_main_id 與 infraredId）
     * 說明：
     * - 僅封裝 new + 設欄位 + save 的便捷方法；外部請先以
     *   {@link #existsUnfinishedRequestForInfrared(Long)} 做裝置忙碌檢查，再呼叫本方法。
     * - 回傳 true 表示成功寫入。
     *
     * @param containerMainId 對應 container_main.id（必填）
     * @param infraredId      對應 infrared.id（必填；例如固定使用 3L）
     * @return 是否建立成功
     */
    boolean createMeasureRequestForContainer(Long containerMainId, Long infraredId);
}

