package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.AlarmItem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * AlarmItemRepository
 * ------------------------------------------------------------
 * Domain 語意化的存取介面（DDD Repository）
 * - 保留基本 CRUD
 * - 針對 alarm_item 的兩個關鍵旗標（is_triggered, want_plc_trigger）
 *   提供「值變才更新」的語意方法，避免無效 UPDATE 與多餘 log
 * - 提供「佇列領取（FOR UPDATE SKIP LOCKED）」以支援多 worker 無重複
 *
 * ⚠️ 交易建議：
 * - claimPendingForPlc(...) 必須在 @Transactional 環境下呼叫，
 *   實作端會用「SELECT ... FOR UPDATE SKIP LOCKED」領取工作。
 * - 清旗標 / 回補佇列等批次更新也建議在同一交易內完成，提高一致性。
 */
public interface AlarmItemRepository {

    // =========================
    // 基本 CRUD
    // =========================

    /** 依 PK 取得 */
    Optional<AlarmItem> findById(Long id);

    /** 新增（成功回 true） */
    boolean save(AlarmItem entity);

    /** 以 PK 更新（成功回 true） */
    boolean update(AlarmItem entity);

    /** 以 PK 刪除（成功回 true） */
    boolean deleteById(Long id);

    /** 全量查詢（僅限工具或小量資料場景使用） */
    List<AlarmItem> findAll();


    // =========================
    // 快速查找
    // =========================

    /** 依 global_code 取得（建議一般情境都用這個） */
    Optional<AlarmItem> findByGlobalCode(int globalCode);

    /** 依 local_code 取得（建議一般情境都用這個） */
    Optional<AlarmItem> findByLocalCode(int localCode);

    /**
     * 取得目前觸發中的清單快照（is_triggered=1）
     * @param limit 最大回傳數量（避免一次取太多）
     */
    List<AlarmItem> findTriggeredSnapshot(int limit);

    /**
     * 取得目前「待送 PLC」清單快照（不加鎖）
     * 條件：want_plc_trigger=1 AND allow_plc_trigger=1 AND enabled=1
     */
    List<AlarmItem> findPendingForPlcSnapshot(int limit);


    // =========================
    // 值變才更新（避免無效 UPDATE 與多餘 log）
    // =========================

    /**
     * 設定 is_triggered 的值（僅在「值不同」時才更新）
     * @return true = 有變更並完成更新；false = 值相同未更新
     *
     * 用途：設備/邏輯回報觸發（TRIGGER/CLEAR）
     */
    boolean setTriggeredIfChanged(int globalCode, boolean triggered);

    /**
     * 設定 want_plc_trigger 的值（僅在「值不同」時才更新）
     * 會同時檢查 enabled=1 & allow_plc_trigger=1 才會成功更新。
     * @return true = 有變更並完成更新；false = 值相同或不允許更新（被 disabled 或不允許主動觸發）
     *
     * 用途：我方排入/取消主動下發 PLC 的佇列（PLC_ON / PLC_OFF）
     */
    boolean setWantPlcIfAllowed(int globalCode, boolean wantOn);


    // =========================
    // 佇列領取 & 後續更新（併發友善）
    // =========================

    /**
     * 領取待送 PLC 的工作（行鎖 + SKIP LOCKED）
     * 條件：want_plc_trigger=1 AND allow_plc_trigger=1 AND enabled=1
     * 實作端將使用「SELECT ... FOR UPDATE SKIP LOCKED」。
     *
     * ⚠️ 必須在同一個 @Transactional 交易中呼叫，否則鎖不生效。
     * 回傳的 AlarmItem 至少要帶 id, globalCode（其餘欄位可選）
     */
    List<AlarmItem> claimPendingForPlc(int limit);

    /**
     * 批次清除 want_plc_trigger（常在成功送出 PLC 後立刻清 0）
     * @return 受影響列數
     */
    int clearWantPlcByIds(Collection<Long> ids);

    /**
     * 批次把指定 global_code 清單「回補」進待送佇列（送失敗重試用）
     * 僅在當前 want_plc_trigger=0 且 enabled=1 & allow_plc_trigger=1 才會置為 1
     * @return 受影響列數
     */
    int reenqueueForPlcByGlobalCodes(Collection<Integer> globalCodes);


    // =========================
    // 進階查詢（依需要擴充）
    // =========================

    /**
     * 依設備查目前觸發中的清單（is_triggered=1）
     * @param equipment 'WIP' | 'ZIPA' | 'ZIPB' | 'FSK6001'
     */
    List<AlarmItem> findTriggeredByEquipment(String equipment, int limit);

    /**
     * 依類型＆設備查詢某段編號（方便用 global_code 區間快速篩選）
     * 例如：ALARM/ZIPA = 15001..20000
     */
    List<AlarmItem> findByTypeAndEquipmentRange(String type, String equipment, int offset, int limit);
}
