package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.application.dto.query.LocationTrackingQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.infra.entity.LocationTracking;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * LocationTracking 資料存取介面
 * - 記錄容器目前所在位置的快照
 * - 每個容器僅對應一筆資料（即時狀態）
 * - 支援 CRUD 與業務查詢方法
 */
public interface LocationTrackingRepository {

    // === 基本查詢 ===

    /**
     * 根據主鍵 ID 查詢單筆資料
     *
     * @param id 資料主鍵
     * @return 單筆快照紀錄
     */
    Optional<LocationTracking> findById(Long id);

    /**
     * 查詢所有資料（不含條件）
     *
     * @return 所有快照清單
     */
    List<LocationTracking> findAll();

    /**
     * 根據查詢條件查詢清單（不使用分頁）
     *
     * @param query 查詢條件
     * @return 快照清單
     */
    List<LocationTracking> findByCondition(LocationTrackingQuery query);

    /**
     * 根據查詢條件查詢分頁資料
     *
     * @param query 查詢條件
     * @return 分頁結果
     */
    PageResult<LocationTracking> findPageByCondition(LocationTrackingQuery query);


    // === 資料異動 ===

    /**
     * 儲存新資料
     *
     * @param entity 快照實體
     * @return 是否成功
     */
    boolean save(LocationTracking entity);

    /**
     * 根據主鍵 ID 更新資料
     *
     * @param entity 含主鍵的更新資料
     * @return 是否成功
     */
    boolean update(LocationTracking entity);

    /**
     * 根據主鍵 ID 刪除資料
     *
     * @param id 資料主鍵
     * @return 是否成功
     */
    boolean deleteById(Long id);

    /**
     * 根據 containerMainId 刪除快照追蹤資料
     * - 通常在容器報廢或移除時使用
     *
     * @param containerMainId 容器 ID
     * @return 是否成功
     */
    boolean deleteByContainerMainId(Long containerMainId);

    /**
     * 更新指定容器目前位置
     * - 若該容器已有追蹤紀錄則更新，否則新增
     *
     * @param containerMainId 容器 ID
     * @param locationPointId 位置 ID
     * @param flowId          對應的流程來源 ID，可為 NULL
     * @return 是否成功
     */
    boolean updateLocation(Long containerMainId, Long locationPointId, Long flowId);


    // === 條件查詢（容器、位置對應）===

    /**
     * 查詢指定位置是否有容器佔據（1 對多）
     *
     * @param locationPointId 位置 ID
     * @return 快照紀錄
     */
    Optional<LocationTracking> findByLocationPointId(Long locationPointId);

    /**
     * 查詢指定容器目前的追蹤紀錄（1 對 1 關係）
     *
     * @param containerMainId 容器 ID
     * @return 快照紀錄
     */
    Optional<LocationTracking> findByContainerMainId(Long containerMainId);

    /**
     * 根據 containerMainId 反查 entryType（來源類型）
     * - 依據實作需求查詢關聯欄位
     *
     * @param containerMainId 容器 ID
     * @return EntryType 字串（如 ASE / UI / SYSTEM 等）
     */
    Optional<String> findEntryTypeByContainerId(Long containerMainId);


    // === 業務輔助查詢方法（推薦使用）===

    /**
     * 判斷指定位置代碼是否有容器佔據
     * - 內部會先查詢 location_point 的 ID，再查是否有對應的快照記錄
     *
     * @param locationId 位置代碼（如 1）
     * @return true 表示該位置目前有容器
     */
    boolean hasContainerAtLocationId(Long locationId);

    /**
     * 判斷指定位置代碼是否有容器佔據
     * - 內部會先查詢 location_point 的 ID，再查是否有對應的快照記錄
     *
     * @param locationName 位置代碼（如 Site#1）
     * @return true 表示該位置目前有容器
     */
    boolean hasContainerAtLocationName(String locationName);

    /**
     * 查詢目前位於指定 transfer 裝置上的容器 ID
     * - 須依據 transferId 轉為 Location Code（如 Transfer#1）進行查詢
     *
     * @param transferId Transfer 裝置 ID
     * @return 容器 ID（若有）
     */
    Optional<Long> findContainerOnTransfer(Long transferId);

    /**
     * 查詢目前位於指定 gripper 裝置上的容器 ID
     * - 須依據 gripperId 轉為 Location Code（如 Gripper#1）進行查詢
     *
     * @param gripperId Gripper 裝置 ID
     * @return 容器 ID（若有）
     */
    Optional<Long> findContainerOnGripper(Long gripperId);

    /**
     * 查詢指定位置名稱上的容器 ID（若有）
     *
     * @param locationName 位置名稱（如 Site#1）
     * @return 容器 ID（若存在）
     */
    Optional<Long> findContainerAtLocationName(String locationName);

    /**
     * 將指定站點設為「無帳」（Vacant）。
     * - 若當前就無帳，亦視為成功（冪等 true）
     * - 建議將 reason 記錄在日誌或審計表（此處先以 log 紀錄）
     */
    boolean vacantByLocationName(String locationName, String reason);

    /**
     * 以 location_point_id 清帳（冪等）
     */
    boolean vacantByLocationId(Long locationPointId, String reason);

    /**
     * 取得目前所有在位容器的 alias_code 清單（以 LocationTracking 為準）
     * - 僅回傳非空白 alias_code
     */
    List<String> findAllPresentAliasCodes();

    List<String> findPresentAliasCodesNot272829();

    List<LocationTracking> findContainersByWorkingBeamId(long id);

    int countEmptyOwnStorage();


}
