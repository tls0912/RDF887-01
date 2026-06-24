package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.application.dto.query.LocationTrackingQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.common.util.LambdaQueryHelper;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.infra.entity.LocationTracking;
import com.czkuo.rdf88701.infra.mapper.LocationPointMapper;
import com.czkuo.rdf88701.infra.mapper.LocationTrackingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * LocationTracking 資料存取實作
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class LocationTrackingRepositoryImpl implements LocationTrackingRepository {

    private final LocationTrackingMapper locationTrackingMapper;
    private final LocationPointMapper locationPointMapper;

    /**
     * 根據主鍵 ID 查詢單筆資料
     *
     * @param id 主鍵 ID
     * @return 對應的紀錄（若無則為空）
     */
    @Override
    public Optional<LocationTracking> findById(Long id) {
        return Optional.ofNullable(locationTrackingMapper.selectById(id));
    }

    /**
     * 根據查詢條件查詢清單（不分頁）
     *
     * @param query 查詢條件
     * @return 符合條件的清單
     */
    @Override
    public List<LocationTracking> findByCondition(LocationTrackingQuery query) {
        LambdaQueryHelper<LocationTracking> helper = buildQueryWrapper(query);
        int offset = (query.getPageNum() - 1) * query.getPageSize();
        helper.getWrapper().last("LIMIT " + offset + "," + query.getPageSize());
        return locationTrackingMapper.selectList(helper.getWrapper());
    }

    /**
     * 根據查詢條件查詢分頁結果
     *
     * @param query 查詢條件（含 pageNum、pageSize）
     * @return PageResult 包含總筆數與當頁資料
     */
    @Override
    public PageResult<LocationTracking> findPageByCondition(LocationTrackingQuery query) {
        LambdaQueryHelper<LocationTracking> helper = buildQueryWrapper(query);
        Page<LocationTracking> page = new Page<>(query.getSafePageNum(), query.getSafePageSize());
        IPage<LocationTracking> result = locationTrackingMapper.selectPage(page, helper.getWrapper());
        return new PageResult<>(query.getSafePageNum(), query.getSafePageSize(), result.getTotal(), result.getRecords());
    }

    /**
     * 建立查詢條件 Wrapper（依據 query 欄位條件建構）
     */
    private LambdaQueryHelper<LocationTracking> buildQueryWrapper(LocationTrackingQuery query) {
        return LambdaQueryHelper.<LocationTracking>of()
                .eqIfPresent(LocationTracking::getId, query::getId)
                .inIfPresent(LocationTracking::getId, query::getIdList)
                .eqIfPresent(LocationTracking::getContainerMainId, query::getContainerMainId)
                .eqIfPresent(LocationTracking::getLocationPointId, query::getLocationPointId)
                .geIfPresent(LocationTracking::getArrivedTime, query::getArrivedAfter)
                .leIfPresent(LocationTracking::getArrivedTime, query::getArrivedBefore);
    }

    /**
     * 新增一筆 LocationTracking 資料
     *
     * @param entity 資料實體
     * @return 是否新增成功
     */
    @Override
    public boolean save(LocationTracking entity) {
        return locationTrackingMapper.insert(entity) > 0;
    }

    /**
     * 更新 LocationTracking 資料（依據 ID）
     *
     * @param entity 欲更新的資料（含 ID）
     * @return 是否更新成功
     */
    @Override
    public boolean update(LocationTracking entity) {
        return locationTrackingMapper.updateById(entity) > 0;
    }

    /**
     * 根據 ID 刪除資料
     *
     * @param id 欲刪除的主鍵 ID
     * @return 是否刪除成功
     */
    @Override
    public boolean deleteById(Long id) {
        return locationTrackingMapper.deleteById(id) > 0;
    }

    /**
     * 查詢所有 LocationTracking 紀錄
     *
     * @return 所有資料清單
     */
    @Override
    public List<LocationTracking> findAll() {
        return locationTrackingMapper.selectList(null);
    }

    /**
     * 根據位置 ID 查詢目前是否有 container 佔據
     *
     * @param locationPointId 位置主鍵
     * @return 若該位置有容器，則回傳快照追蹤資料
     */
    @Override
    public Optional<LocationTracking> findByLocationPointId(Long locationPointId) {
        LambdaQueryWrapper<LocationTracking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LocationTracking::getLocationPointId, locationPointId);
        return Optional.ofNullable(locationTrackingMapper.selectOne(wrapper));
    }


    /**
     * 查詢指定容器目前的 tracking 資料（快照）
     *
     * @param containerMainId 容器主 ID
     * @return 若存在則回傳該容器當前位置
     */
    @Override
    public Optional<LocationTracking> findByContainerMainId(Long containerMainId) {
        return Optional.ofNullable(
                locationTrackingMapper.selectOne(
                        LambdaQueryHelper.<LocationTracking>of()
                                .eqIfPresent(LocationTracking::getContainerMainId, () -> containerMainId)
                                .getWrapper()
                )
        );
    }

    /**
     * 更新指定容器的 tracking 資料（位置與對應 flow）
     *
     * @param containerMainId  容器主鍵
     * @param locationPointId  新位置 ID
     * @param flowId           對應的 location_flow ID
     * @return 是否更新成功
     */
    @Override
    public boolean updateLocation(Long containerMainId, Long locationPointId, Long flowId) {
        LocationTracking update = new LocationTracking();
        update.setLocationPointId(locationPointId);
        update.setFlowId(flowId);
        update.setLastVerifiedTime(LocalDateTime.now());

        LambdaQueryWrapper<LocationTracking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LocationTracking::getContainerMainId, containerMainId);

        return locationTrackingMapper.update(update, wrapper) > 0;
    }

    /**
     * 查詢容器當前 tracking 對應的建帳來源（entryType）
     *
     * @param containerMainId 容器主鍵
     * @return 該 container 所對應 flow 的 entry_type（如 PLC, MANUAL 等）
     */
    @Override
    public Optional<String> findEntryTypeByContainerId(Long containerMainId) {
        // Step 1: 先查 tracking，取得 flowId
        Optional<LocationTracking> trackingOpt = findByContainerMainId(containerMainId);
        if (trackingOpt.isEmpty() || trackingOpt.get().getFlowId() == null) {
            return Optional.empty();
        }

        Long flowId = trackingOpt.get().getFlowId();

        // Step 2: 查 flow 表取得 entry_type
        return Optional.ofNullable(locationTrackingMapper.findEntryTypeByFlowId(flowId));
    }

    /**
     * 根據 containerMainId 刪除對應快照紀錄
     *
     * @param containerMainId 容器主鍵
     * @return 是否成功刪除資料
     */
    @Override
    public boolean deleteByContainerMainId(Long containerMainId) {
        LambdaQueryWrapper<LocationTracking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LocationTracking::getContainerMainId, containerMainId);
        return locationTrackingMapper.delete(wrapper) > 0;
    }

    /**
     * 檢查指定 location id 是否已有容器佔據
     *
     * @param locationId 位置代碼
     * @return true 表示該位置已有容器
     */
    @Override
    public boolean hasContainerAtLocationId(Long locationId) {
        if (locationId == null) return false;
        return findByLocationPointId(locationId).isPresent();
    }

    /**
     * 檢查指定 location name 是否已有容器佔據
     *
     * @param locationName 位置代碼
     * @return true 表示該位置已有容器
     */
    @Override
    public boolean hasContainerAtLocationName(String locationName) {
        Long locationId = locationPointMapper.selectIdByName(locationName);
        if (locationId == null) return false;
        return findByLocationPointId(locationId).isPresent();
    }

    /**
     * 查詢指定 Transfer ID 對應的位置上是否有容器（取第一個）
     *
     * @param transferId Transfer 裝置 ID
     * @return 若有則回傳對應容器 ID
     */
    @Override
    public Optional<Long> findContainerOnTransfer(Long transferId) {
        List<Long> locationIds = locationPointMapper.selectIdsByTransferId(transferId);
        if (locationIds == null || locationIds.isEmpty()) return Optional.empty();

        return locationIds.stream()
                .map(this::findByLocationPointId)
                .filter(Optional::isPresent)
                .map(opt -> opt.get().getContainerMainId())
                .findFirst();
    }

    /**
     * 查詢指定 Gripper ID 對應的位置上是否有容器（取第一個）
     *
     * @param gripperId Gripper 裝置 ID
     * @return 若有則回傳對應容器 ID
     */
    @Override
    public Optional<Long> findContainerOnGripper(Long gripperId) {
        List<Long> locationIds = locationPointMapper.selectIdsByGripperId(gripperId);
        if (locationIds == null || locationIds.isEmpty()) return Optional.empty();

        return locationIds.stream()
                .map(this::findByLocationPointId)
                .filter(Optional::isPresent)
                .map(opt -> opt.get().getContainerMainId())
                .findFirst();
    }

    /**
     * 查詢指定位置名稱上的容器 ID（若有）
     *
     * @param locationName 位置名稱（如 Site#1）
     * @return 容器 ID（若存在）
     */
    @Override
    public Optional<Long> findContainerAtLocationName(String locationName) {
        Long locationId = locationPointMapper.selectIdByName(locationName);
        if (locationId == null) return Optional.empty();

        return findByLocationPointId(locationId)
                .map(LocationTracking::getContainerMainId);
    }

    /**
     * 將指定站點設為「無帳」（Vacant）。
     * - 若當前就無帳，亦視為成功（冪等 true）
     * - 建議將 reason 記錄在日誌或審計表（此處先以 log 紀錄）
     */
    @Override
    public boolean vacantByLocationName(String locationName, String reason) {
        Long locationId = locationPointMapper.selectIdByName(locationName);
        if (locationId == null) {
            log.info("[LocationVacant] {} not found, reason={}", locationName, reason);
            return true; // 當作已空
        }
        return vacantByLocationId(locationId, reason);
    }

    /**
     * 以 location_point_id 清帳（冪等）
     */
    @Override
    public boolean vacantByLocationId(Long locationPointId, String reason) {
        if (locationPointId == null) return true;

        LambdaQueryWrapper<LocationTracking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LocationTracking::getLocationPointId, locationPointId);

        int affected = locationTrackingMapper.delete(wrapper);
        if (affected > 0) {
            log.info("[LocationVacant] cleared locationPointId={}, rows={}, reason={}", locationPointId, affected, reason);
        } else {
            log.info("[LocationVacant] already empty locationPointId={}, reason={}", locationPointId, reason);
        }
        // 冪等視為成功
        return true;
    }

    @Override
    public List<String> findAllPresentAliasCodes() {
        try {
            List<String> list = locationTrackingMapper.selectAllPresentAliasCodes();
            // 避免 null；同時去掉空白字串
            return list == null ? List.of()
                    : list.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
        } catch (Exception e) {
            log.warn("[LocationTracking] findAllPresentAliasCodes query failed: {}", e.getMessage(), e);
            return List.of();
        }
    }
    @Override
    public List<String> findPresentAliasCodesNot272829() {
        try {
            List<String> list = locationTrackingMapper.selectPresentAliasCodesNot272829();
            // 避免 null；同時去掉空白字串
            return list == null ? List.of()
                    : list.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
        } catch (Exception e) {
            log.warn("[LocationTracking] findPresentAliasCodesNot272829 query failed: {}", e.getMessage(), e);
            return List.of();
        }
    }
    @Override
    public List<LocationTracking> findContainersByWorkingBeamId(long id) {
        try {
            return locationTrackingMapper.selectContainersByWorkingBeamId(id);
        } catch (Exception e) {
            log.warn("[LocationTracking] selectContainersByWorkingBeamId query failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public int countEmptyOwnStorage() {
        try {
            return locationTrackingMapper.countEmptyOwnStorage();
        } catch (Exception e) {
            log.warn("[LocationTracking] countEmptyOwnStorage query failed: {}", e.getMessage(), e);
            return 0;
        }
    }
}
