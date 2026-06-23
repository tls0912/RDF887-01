package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.czkuo.rdf88701.domain.repository.AlarmItemRepository;
import com.czkuo.rdf88701.infra.entity.AlarmItem;
import com.czkuo.rdf88701.infra.mapper.AlarmItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * AlarmItemRepositoryImpl
 * ------------------------------------------------------------
 * - 對應 alarm_item 的存取實作。
 * - 重點：兩個旗標的「值變才更新」與「待送 PLC 佇列領取」（行鎖 + SKIP LOCKED）。
 * - 語意化方法讓上層 Service 更簡潔、也避免無效 UPDATE 與多餘 log。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AlarmItemRepositoryImpl implements AlarmItemRepository {

    private final AlarmItemMapper alarmItemMapper;

    // =========================
    // 基本 CRUD
    // =========================

    /** 依 PK 取得 */
    @Override
    public Optional<AlarmItem> findById(Long id) {
        return Optional.ofNullable(alarmItemMapper.selectById(id));
    }

    /** 新增（成功回 true） */
    @Override
    public boolean save(AlarmItem entity) {
        return alarmItemMapper.insert(entity) > 0;
    }

    /** 以 PK 更新（成功回 true） */
    @Override
    public boolean update(AlarmItem entity) {
        return alarmItemMapper.updateById(entity) > 0;
    }

    /** 以 PK 刪除（成功回 true） */
    @Override
    public boolean deleteById(Long id) {
        return alarmItemMapper.deleteById(id) > 0;
    }

    /** 全量查詢（僅限工具或小量資料場景使用） */
    @Override
    public List<AlarmItem> findAll() {
        return alarmItemMapper.selectList(new LambdaQueryWrapper<>());
    }

    // =========================
    // 快速查找
    // =========================

    /** 依 global_code 取得（建議常用） */
    @Override
    public Optional<AlarmItem> findByGlobalCode(int globalCode) {
        return Optional.ofNullable(
                alarmItemMapper.selectOne(
                        new LambdaQueryWrapper<AlarmItem>()
                                .eq(AlarmItem::getGlobalCode, globalCode)
                                .last("LIMIT 1")
                )
        );
    }

    /** 依 local_code 取得（建議常用） */
    @Override
    public Optional<AlarmItem> findByLocalCode(int localCode) {
        return Optional.ofNullable(
                alarmItemMapper.selectOne(
                        new LambdaQueryWrapper<AlarmItem>()
                                .eq(AlarmItem::getLocalCode, localCode)
                                .last("LIMIT 1")
                )
        );
    }

    /** 目前觸發中的清單快照（is_triggered=1） */
    @Override
    public List<AlarmItem> findTriggeredSnapshot(int limit) {
        return alarmItemMapper.selectList(
                new LambdaQueryWrapper<AlarmItem>()
                        .eq(AlarmItem::getIsTriggered, true)
                        .orderByDesc(AlarmItem::getUpdatedAt)
                        .last("LIMIT " + Math.max(1, limit))
        );
    }

    /** 目前「待送 PLC」快照（不加鎖） */
    @Override
    public List<AlarmItem> findPendingForPlcSnapshot(int limit) {
        return alarmItemMapper.selectList(
                new LambdaQueryWrapper<AlarmItem>()
                        .eq(AlarmItem::getWantPlcTrigger, true)
                        .eq(AlarmItem::getAllowPlcTrigger, true)
                        .eq(AlarmItem::getEnabled, true)
                        .orderByAsc(AlarmItem::getGlobalCode)
                        .last("LIMIT " + Math.max(1, limit))
        );
    }

    // =========================
    // 值變才更新（避免無效 UPDATE 與多餘 log）
    // =========================

    /**
     * 設定 is_triggered；僅在值不同時更新。
     * 回 true 表示有變更（0→1 或 1→0），false 表示值相同未更新。
     */
    @Override
    public boolean setTriggeredIfChanged(int globalCode, boolean triggered) {
        int changed = alarmItemMapper.update(
                null,
                new LambdaUpdateWrapper<AlarmItem>()
                        .eq(AlarmItem::getGlobalCode, globalCode)
                        .ne(AlarmItem::getIsTriggered, triggered) // 只有值不同時才更新
                        .set(AlarmItem::getIsTriggered, triggered)
        );
        return changed > 0;
    }

    /**
     * 設定 want_plc_trigger；僅在值不同且允許時（enabled=1 & allow=1）更新。
     * 回 true 表示有變更；false 可能是值相同或不允許更新。
     */
    @Override
    public boolean setWantPlcIfAllowed(int globalCode, boolean wantOn) {
        int changed = alarmItemMapper.update(
                null,
                new LambdaUpdateWrapper<AlarmItem>()
                        .eq(AlarmItem::getGlobalCode, globalCode)
                        .eq(AlarmItem::getEnabled, true)
                        .eq(AlarmItem::getAllowPlcTrigger, true)
                        .ne(AlarmItem::getWantPlcTrigger, wantOn)
                        .set(AlarmItem::getWantPlcTrigger, wantOn)
        );
        return changed > 0;
    }

    // =========================
    // 佇列領取 & 後續更新（併發友善）
    // =========================

    /**
     * 領取待送 PLC 的工作（行鎖 + SKIP LOCKED）。
     * 必須在 @Transactional 交易中呼叫才會生效。
     */
    @Override
    @Transactional
    public List<AlarmItem> claimPendingForPlc(int limit) {
        int n = Math.max(1, limit);
        // 這裡透過 Mapper 的自訂 SQL：SELECT ... FOR UPDATE SKIP LOCKED LIMIT #{limit}
        return alarmItemMapper.lockAndFetchPending(n);
    }

    /** 成功送 PLC 後，批次清除 want_plc_trigger=0 */
    @Override
    @Transactional
    public int clearWantPlcByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        return alarmItemMapper.update(
                null,
                new LambdaUpdateWrapper<AlarmItem>()
                        .in(AlarmItem::getId, ids)
                        .set(AlarmItem::getWantPlcTrigger, false)
        );
    }

    /** 送失敗 → 回補佇列（僅在當前為 0 且允許的情況下置為 1） */
    @Override
    @Transactional
    public int reenqueueForPlcByGlobalCodes(Collection<Integer> globalCodes) {
        if (globalCodes == null || globalCodes.isEmpty()) return 0;
        return alarmItemMapper.update(
                null,
                new LambdaUpdateWrapper<AlarmItem>()
                        .in(AlarmItem::getGlobalCode, globalCodes)
                        .eq(AlarmItem::getWantPlcTrigger, false)
                        .eq(AlarmItem::getAllowPlcTrigger, true)
                        .eq(AlarmItem::getEnabled, true)
                        .set(AlarmItem::getWantPlcTrigger, true)
        );
    }

    // =========================
    // 進階查詢
    // =========================

    /** 依設備查目前觸發中的清單（is_triggered=1） */
    @Override
    public List<AlarmItem> findTriggeredByEquipment(String equipment, int limit) {
        return alarmItemMapper.selectList(
                new LambdaQueryWrapper<AlarmItem>()
                        .eq(AlarmItem::getEquipment, equipment)
                        .eq(AlarmItem::getIsTriggered, true)
                        .orderByDesc(AlarmItem::getUpdatedAt)
                        .last("LIMIT " + Math.max(1, limit))
        );
    }

    /** 依 type/equipment 查詢分頁（對應編號區段；以 global_code 升冪） */
    @Override
    public List<AlarmItem> findByTypeAndEquipmentRange(String type, String equipment, int offset, int limit) {
        int off = Math.max(0, offset);
        int n = Math.max(1, limit);
        return alarmItemMapper.selectList(
                new LambdaQueryWrapper<AlarmItem>()
                        .eq(AlarmItem::getType, type)
                        .eq(AlarmItem::getEquipment, equipment)
                        .orderByAsc(AlarmItem::getGlobalCode)
                        .last("LIMIT " + off + "," + n)
        );
    }
}
