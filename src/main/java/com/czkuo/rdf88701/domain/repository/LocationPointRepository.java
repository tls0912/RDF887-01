package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.application.dto.query.LocationPointQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.domain.dto.wip.WipSlotDetailDTO;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.entity.LocationReservationRecord;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 儲位（LocationPoint）資料存取介面
 *
 * 定義儲位查詢、狀態標記（occupied/locked/reserved/enabled）、
 * 以及提供各種可用儲位的選擇 API（含排除條件、隨機選點、排除有效預約等）。
 */
public interface LocationPointRepository {

    /** 依主鍵查詢 */
    Optional<LocationPoint> findById(Long id);

    /** 依 name 查詢（如 Site#9、Crane#1 等） */
    Optional<LocationPoint> findByName(String name);

    /** 條件查詢（不分頁） */
    List<LocationPoint> findByCondition(LocationPointQuery query);

    /** 條件查詢（分頁） */
    PageResult<LocationPoint> findPageByCondition(LocationPointQuery query);

    /** 新增 */
    boolean save(LocationPoint entity);

    /** 更新 */
    boolean update(LocationPoint entity);

    /** 刪除 */
    boolean deleteById(Long id);

    /** 查詢全部 */
    List<LocationPoint> findAll();

    /**
     * 查找任一可用儲位（最基本條件）
     * 條件：STORAGE / enabled='Y' / is_occupied='N' / is_locked='N' / is_reserved='N'
     */
    Optional<LocationPoint> findFirstAvailableStorage();

    /**
     * 查找符合過濾條件的可用儲位（可依 zoneCode、preferredStatus 過濾）
     * preferredStatus 支援 'ANY'
     */
    Optional<LocationPoint> findFirstAvailableStorageWithFilter(String zoneCode, String preferredStatus);

    /** 取前 N 筆可用儲位（固定排序：level ASC, bank DESC, bay ASC） */
    List<LocationPoint> findAvailableStorageList(int limit);

    /** 依 zone_code 與六碼 code 進行精準匹配 */
    Optional<LocationPoint> findByZoneCodeAndLocationCode(String zoneCode, String locationCode);

    /**
     * 查找全部可用儲位（可排除指定 ID），排序：level ASC, bank DESC, bay ASC
     */
    List<LocationPoint> findAllAvailableStorageExcluding(Set<Long> excludedLocationIds);

    /**
     * 隨機查找指定數量的可用儲位（可排除指定 ID）
     */
    List<LocationPoint> findRandomAvailableStorageListExcluding(int limit, Set<Long> excludedLocationIds);

    /**
     * 隨機查找指定數量的可用儲位：
     * - 排除 is_occupied / is_locked / is_reserved
     * - 排除存在「有效預約」的儲位（fulfilled=0 AND cancelled=0 AND expired=0，且未過期）
     * - 可排除指定 ID
     */
    List<LocationPoint> findAvailableStorageWithoutReservationExcluding(int limit, Set<Long> excludedLocationIds);

    /**
     * 只取「一個」可用儲位（排除指定 ID + 排除有效預約）
     * - 典型用於「建單前先預留」的原子化流程
     */
    Optional<LocationPoint> findAvailableStorageWithoutReservationExcludingOne(Set<Long> excludedLocationIds);

    /** 標記為已佔用（is_occupied='Y'） */
    void markOccupied(Long locationPointId);

    /** 標記為空位（is_occupied='N'） */
    void markVacant(Long locationPointId);

    /** 標記為啟用（enabled='Y'） */
    void markEnabled(Long locationPointId);

    /** 標記為停用（enabled='N'） */
    void markDisabled(Long locationPointId);

    /** 標記為鎖定（is_locked='Y'） */
    void markLocked(Long locationPointId);

    /** 標記為解鎖（is_locked='N'） */
    void markUnlocked(Long locationPointId);

    /** 標記為預約中（is_reserved='Y'） */
    void markReserved(Long locationPointId);

    /** 取消預約（is_reserved='N'） */
    void markUnreserved(Long locationPointId);

    /** 取得所有儲格現況（UI、盤點、S004 使用） */
    List<WipSlotDetailDTO> findAllSlotDetails();

    /** 依區域取得儲格現況 */
    List<WipSlotDetailDTO> findSlotDetailsByZone(String zoneCode);

    /** 取得所有帳實不一致的儲格（自動盤點） */
    List<WipSlotDetailDTO> findMismatchedSlotDetails();
}
