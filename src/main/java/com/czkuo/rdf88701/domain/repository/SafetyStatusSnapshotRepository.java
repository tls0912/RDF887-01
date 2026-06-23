package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.SafetyStatusSnapshot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SafetyStatusSnapshotRepository
 *
 * 對應資料表：safety_status_snapshot（PK = point_id）
 * - 儲存每個安全點位目前的觸發狀態與時間戳
 * - 提供以 pointId（即 safety_point.id）為主鍵的查詢/更新能力
 */
public interface SafetyStatusSnapshotRepository {

    // ===================== 讀取 =====================

    /**
     * 依 pointId（= PK）查詢一筆快照。
     */
    Optional<SafetyStatusSnapshot> findByPointId(Long pointId);

    /**
     * 與舊版介面相容的別名。
     * 建議改用 {@link #findByPointId(Long)}。
     */
    @Deprecated
    default Optional<SafetyStatusSnapshot> findById(Long id) {
        return findByPointId(id);
    }

    /**
     * 查詢全部快照。
     */
    List<SafetyStatusSnapshot> findAll();

    /**
     * 依多個 pointId 批次查詢快照。
     */
    List<SafetyStatusSnapshot> findAllByPointIds(List<Long> pointIds);

    /**
     * 計算總筆數。
     */
    long count();

    // ===================== 新增 / 更新 =====================

    /**
     * 新增單筆（若已存在相同 pointId 會失敗，視底層 Mapper 規劃）。
     */
    boolean save(SafetyStatusSnapshot entity);

    /**
     * 批次新增（建議使用 MyBatis-Plus 的自訂 batch 插入）。
     */
    boolean saveBatch(List<SafetyStatusSnapshot> entities);

    /**
     * 新增或更新（存在則 update，不存在則 insert）。
     * 典型實作可用 MySQL ON DUPLICATE KEY UPDATE 或以程式先查再決定。
     */
    boolean upsert(SafetyStatusSnapshot entity);

    /**
     * 依主鍵更新整筆資料。
     */
    boolean update(SafetyStatusSnapshot entity);

    /**
     * 只更新「觸發狀態」與兩個時間戳欄位（常見於輪詢寫回邏輯）。
     *
     * @param pointId        對應 safety_point.id
     * @param triggered      是否觸發（true=Y / false=N）
     * @param lastChangeTime 最後狀態變化時間（狀態從 Y->N 或 N->Y 時更新）
     * @param lastPollTime   最後輪詢到資料時間（每次輪詢都更新）
     */
    boolean updateTriggerAndTimes(Long pointId,
                                  boolean triggered,
                                  LocalDateTime lastChangeTime,
                                  LocalDateTime lastPollTime);

    // ===================== 刪除 =====================

    /**
     * 依 pointId 刪除。
     */
    boolean deleteByPointId(Long pointId);

    /**
     * 與舊版介面相容的別名。
     * 建議改用 {@link #deleteByPointId(Long)}。
     */
    @Deprecated
    default boolean deleteById(Long id) {
        return deleteByPointId(id);
    }

    // ===================== 小工具（可選） =====================

    /**
     * 將 boolean 轉成資料表慣用的 'Y' / 'N'。
     * 供實作端在 Mapper/SQL 中重用。
     */
    default char toYN(boolean v) {
        return v ? 'Y' : 'N';
    }
}
