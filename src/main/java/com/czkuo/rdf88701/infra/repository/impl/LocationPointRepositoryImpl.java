package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.application.dto.query.LocationPointQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.common.util.LambdaQueryHelper;
import com.czkuo.rdf88701.domain.dto.wip.WipSlotDetailDTO;
import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.mapper.LocationPointMapper;
import com.czkuo.rdf88701.infra.mapper.LocationReservationRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;

/**
 * LocationPoint 儲位資料存取實作
 *
 * 說明：
 * - 可用儲位的定義以 DB 欄位為準：STORAGE + enabled='Y' + is_occupied='N' + is_locked='N' + is_reserved='N'
 * - 排除「有效預約」的邏輯交由 Mapper SQL（LEFT JOIN reservation + 條件）負責
 * - 排序（非隨機查找）採：level ASC → bank DESC → bay ASC
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
@RequiredArgsConstructor
public class LocationPointRepositoryImpl implements LocationPointRepository {

    private final LocationPointMapper locationPointMapper;
    // ＊提示：原本的 LocationReservationRecordMapper 成員若未使用，建議刪除以避免 IDE 警告

    /** 依主鍵查詢 */
    @Override
    public Optional<LocationPoint> findById(Long id) {
        return Optional.ofNullable(locationPointMapper.selectById(id));
    }

    /** 依 name 查詢（如 Site#9、Crane#1 等） */
    @Override
    public Optional<LocationPoint> findByName(String name) {
        LambdaQueryWrapper<LocationPoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LocationPoint::getName, name);
        return Optional.ofNullable(locationPointMapper.selectOne(wrapper));
    }

    /** 條件查詢（不分頁） */
    @Override
    public List<LocationPoint> findByCondition(LocationPointQuery query) {
        LambdaQueryHelper<LocationPoint> helper = buildQueryWrapper(query);
        int offset = (query.getPageNum() - 1) * query.getPageSize();
        helper.getWrapper().last("LIMIT " + offset + "," + query.getPageSize());
        return locationPointMapper.selectList(helper.getWrapper());
    }

    /** 條件查詢（分頁） */
    @Override
    public PageResult<LocationPoint> findPageByCondition(LocationPointQuery query) {
        LambdaQueryHelper<LocationPoint> helper = buildQueryWrapper(query);
        Page<LocationPoint> page = new Page<>(query.getSafePageNum(), query.getSafePageSize());
        IPage<LocationPoint> result = locationPointMapper.selectPage(page, helper.getWrapper());
        return new PageResult<>(query.getSafePageNum(), query.getSafePageSize(), result.getTotal(), result.getRecords());
    }

    /**
     * 將 LocationPointQuery 轉為 MP LambdaQueryWrapper
     */
    private LambdaQueryHelper<LocationPoint> buildQueryWrapper(LocationPointQuery query) {
        return LambdaQueryHelper.<LocationPoint>of()
                .eqIfPresent(LocationPoint::getId, query::getId)
                .inIfPresent(LocationPoint::getId, query::getIdList)
                .eqIfPresent(LocationPoint::getZoneCode, query::getZoneCode)
                .likeIfPresent(LocationPoint::getCode, query::getCode)
                .likeIfPresent(LocationPoint::getName, query::getName)
                .eqIfPresent(LocationPoint::getLocationType, query::getLocationType)
                .eqIfPresent(LocationPoint::getEnabled, query::getEnabled)
                .eqIfPresent(LocationPoint::getIsLocked, query::getIsLocked)
                .eqIfPresent(LocationPoint::getIsOccupied, query::getIsOccupied)
                .eqIfPresent(LocationPoint::getIsReserved, query::getIsReserved)
                .eqIfPresent(LocationPoint::getPreferredStatus, query::getPreferredStatus)
                .geIfPresent(LocationPoint::getCreatedTime, query::getCreatedAfter)
                .leIfPresent(LocationPoint::getCreatedTime, query::getCreatedBefore);
    }

    /** 新增 */
    @Override
    public boolean save(LocationPoint entity) {
        return locationPointMapper.insert(entity) > 0;
    }

    /** 更新 */
    @Override
    public boolean update(LocationPoint entity) {
        return locationPointMapper.updateById(entity) > 0;
    }

    /** 刪除 */
    @Override
    public boolean deleteById(Long id) {
        return locationPointMapper.deleteById(id) > 0;
    }

    /** 查全部 */
    @Override
    public List<LocationPoint> findAll() {
        return locationPointMapper.selectList(null);
    }

    /** 取第一個可用儲位（基本條件） */
    @Override
    public Optional<LocationPoint> findFirstAvailableStorage() {
        var w = new LambdaQueryWrapper<LocationPoint>()
                .eq(LocationPoint::getLocationType, "STORAGE")
                .eq(LocationPoint::getEnabled, "Y")
                .eq(LocationPoint::getIsOccupied, "N")
                .eq(LocationPoint::getIsLocked, "N")
                .eq(LocationPoint::getIsReserved, "N")
                .orderByAsc(LocationPoint::getLevel)
                .orderByDesc(LocationPoint::getBank)
                .orderByAsc(LocationPoint::getBay)
                .last("LIMIT 1");
        return Optional.ofNullable(locationPointMapper.selectOne(w));
    }

    /** 取第一個可用儲位（加上 zone + preferredStatus 過濾；preferredStatus 支援 ANY） */
    @Override
    public Optional<LocationPoint> findFirstAvailableStorageWithFilter(String zoneCode, String preferredStatus) {
        var w = new LambdaQueryWrapper<LocationPoint>()
                .eq(LocationPoint::getLocationType, "STORAGE")
                .eq(LocationPoint::getEnabled, "Y")
                .eq(LocationPoint::getIsOccupied, "N")
                .eq(LocationPoint::getIsLocked, "N")
                .eq(LocationPoint::getIsReserved, "N")
                .eq(LocationPoint::getZoneCode, zoneCode)
                .and(ww -> ww.eq(LocationPoint::getPreferredStatus, preferredStatus)
                        .or().eq(LocationPoint::getPreferredStatus, "ANY"))
                .orderByAsc(LocationPoint::getLevel)
                .orderByDesc(LocationPoint::getBank)
                .orderByAsc(LocationPoint::getBay)
                .last("LIMIT 1");
        return Optional.ofNullable(locationPointMapper.selectOne(w));
    }

    /** 取前 N 筆可用儲位（固定排序） */
    @Override
    public List<LocationPoint> findAvailableStorageList(int limit) {
        var w = new LambdaQueryWrapper<LocationPoint>()
                .eq(LocationPoint::getLocationType, "STORAGE")
                .eq(LocationPoint::getEnabled, "Y")
                .eq(LocationPoint::getIsOccupied, "N")
                .eq(LocationPoint::getIsLocked, "N")
                .eq(LocationPoint::getIsReserved, "N")
                .orderByAsc(LocationPoint::getLevel)
                .orderByDesc(LocationPoint::getBank)
                .orderByAsc(LocationPoint::getBay)
                .last("LIMIT " + limit);
        return locationPointMapper.selectList(w);
    }

    /** 依 zone + 六碼 location code 精準查找 */
    @Override
    public Optional<LocationPoint> findByZoneCodeAndLocationCode(String zoneCode, String locationCode) {
        if (zoneCode == null || locationCode == null) return Optional.empty();
        var w = new LambdaQueryWrapper<LocationPoint>()
                .eq(LocationPoint::getZoneCode, zoneCode)
                .eq(LocationPoint::getCode, locationCode)
                .last("LIMIT 1");
        return Optional.ofNullable(locationPointMapper.selectOne(w));
    }

    /**
     * 查找全部可用儲位，並可排除指定 ID；排序：level ASC → bank DESC → bay ASC
     */
    @Override
    public List<LocationPoint> findAllAvailableStorageExcluding(Set<Long> excludedLocationIds) {
        var w = new LambdaQueryWrapper<LocationPoint>()
                .eq(LocationPoint::getLocationType, "STORAGE")
                .eq(LocationPoint::getEnabled, "Y")
                .eq(LocationPoint::getIsOccupied, "N")
                .eq(LocationPoint::getIsLocked, "N")
                .eq(LocationPoint::getIsReserved, "N");

        if (excludedLocationIds != null && !excludedLocationIds.isEmpty()) {
            w.notIn(LocationPoint::getId, excludedLocationIds);
        }

        w.orderByAsc(LocationPoint::getLevel)
                .orderByDesc(LocationPoint::getBank)
                .orderByAsc(LocationPoint::getBay);

        return locationPointMapper.selectList(w);
    }

    /**
     * 隨機查找指定數量的可用儲位（可排除指定 ID）
     */
    @Override
    public List<LocationPoint> findRandomAvailableStorageListExcluding(int limit, Set<Long> excludedLocationIds) {
        var w = new LambdaQueryWrapper<LocationPoint>()
                .eq(LocationPoint::getLocationType, "STORAGE")
                .eq(LocationPoint::getEnabled, "Y")
                .eq(LocationPoint::getIsOccupied, "N")
                .eq(LocationPoint::getIsLocked, "N")
                .eq(LocationPoint::getIsReserved, "N");

        if (excludedLocationIds != null && !excludedLocationIds.isEmpty()) {
            w.notIn(LocationPoint::getId, excludedLocationIds);
        }

        // MP 不支援 RAND() 排序 API，改用 last 拼 SQL
        w.last("ORDER BY RAND() LIMIT " + limit);
        return locationPointMapper.selectList(w);
    }

    /**
     * 隨機查找指定數量的可用儲位，且排除「有效預約」
     * SQL 在 Mapper 層以 LEFT JOIN reservation + 條件(rr.fulfilled=0/rr.cancelled=0/rr.expired=0 AND 未過期) 過濾
     */
    @Override
    public List<LocationPoint> findAvailableStorageWithoutReservationExcluding(int limit, Set<Long> excludedLocationIds) {
        Map<String, Object> p = new HashMap<>();
        p.put("limit", Math.max(1, limit));
        p.put("excludedLocationIds", excludedLocationIds == null ? Collections.emptySet() : excludedLocationIds);
        p.put("forUpdate", Boolean.FALSE);
        return locationPointMapper.findAvailableStorageWithoutReservationExcluding(p);
    }

    /**
     * 只取「一個」可用儲位（排除指定 ID + 排除有效預約）
     * 典型用於：建單前先預留（atomic：選位→建預約→刷新旗標）
     */
    @Override
    public Optional<LocationPoint> findAvailableStorageWithoutReservationExcludingOne(Set<Long> excludedLocationIds) {
        Map<String, Object> p = new HashMap<>();
        p.put("limit", 1);
        p.put("excludedLocationIds", excludedLocationIds == null ? Collections.emptySet() : excludedLocationIds);
        p.put("forUpdate", Boolean.TRUE); // 如非 MySQL 8，改成 FALSE 或移除此參數
        var list = locationPointMapper.findAvailableStorageWithoutReservationExcluding(p);
        return (list == null || list.isEmpty()) ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 標記已佔用（is_occupied='Y'） */
    @Override
    public void markOccupied(Long locationPointId) {
        LocationPoint entity = new LocationPoint();
        entity.setId(locationPointId);
        entity.setIsOccupied("Y");
        locationPointMapper.updateById(entity);
    }

    /** 標記空位（is_occupied='N'） */
    @Override
    public void markVacant(Long locationPointId) {
        LocationPoint entity = new LocationPoint();
        entity.setId(locationPointId);
        entity.setIsOccupied("N");
        locationPointMapper.updateById(entity);
    }

    /** 啟用（enabled='Y'） */
    @Override
    public void markEnabled(Long locationPointId) {
        LocationPoint entity = new LocationPoint();
        entity.setId(locationPointId);
        entity.setEnabled("Y");
        locationPointMapper.updateById(entity);
    }

    /** 停用（enabled='N'） */
    @Override
    public void markDisabled(Long locationPointId) {
        LocationPoint entity = new LocationPoint();
        entity.setId(locationPointId);
        entity.setEnabled("N");
        locationPointMapper.updateById(entity);
    }

    /** 鎖定（is_locked='Y'） */
    @Override
    public void markLocked(Long locationPointId) {
        LocationPoint entity = new LocationPoint();
        entity.setId(locationPointId);
        entity.setIsLocked("Y");
        locationPointMapper.updateById(entity);
    }

    /** 解鎖（is_locked='N'） */
    @Override
    public void markUnlocked(Long locationPointId) {
        LocationPoint entity = new LocationPoint();
        entity.setId(locationPointId);
        entity.setIsLocked("N");
        locationPointMapper.updateById(entity);
    }

    /** 標記預約中（is_reserved='Y'） */
    @Override
    public void markReserved(Long locationPointId) {
        LocationPoint entity = new LocationPoint();
        entity.setId(locationPointId);
        entity.setIsReserved("Y");
        locationPointMapper.updateById(entity);
    }

    /** 取消預約（is_reserved='N'） */
    @Override
    public void markUnreserved(Long locationPointId) {
        LocationPoint entity = new LocationPoint();
        entity.setId(locationPointId);
        entity.setIsReserved("N");
        locationPointMapper.updateById(entity);
    }

    /** 全部儲格現況（UI/盤點/S004） */
    @Override
    public List<WipSlotDetailDTO> findAllSlotDetails() {
        return locationPointMapper.selectAllWipSlotDetails();
    }

    /** 指定區域儲格現況 */
    @Override
    public List<WipSlotDetailDTO> findSlotDetailsByZone(String zoneCode) {
        return locationPointMapper.selectWipSlotDetailsByZone(zoneCode);
    }

    /** 帳實不一致儲格清單（自動盤點） */
    @Override
    public List<WipSlotDetailDTO> findMismatchedSlotDetails() {
        return locationPointMapper.selectMismatchedSlotDetails();
    }
}
