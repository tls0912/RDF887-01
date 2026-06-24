package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.czkuo.rdf88701.domain.repository.SafetyStatusSnapshotRepository;
import com.czkuo.rdf88701.infra.entity.SafetyStatusSnapshot;
import com.czkuo.rdf88701.infra.mapper.SafetyStatusSnapshotMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * SafetyStatusSnapshotRepositoryImpl
 *
 * 對應資料表：safety_status_snapshot（PK = point_id）
 * - 以 pointId 為主鍵做 CRUD
 * - 提供批次/UPSERT 與局部欄位更新（is_triggered / last_change_time / last_poll_time）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
public class SafetyStatusSnapshotRepositoryImpl implements SafetyStatusSnapshotRepository {

    private final SafetyStatusSnapshotMapper safetyStatusSnapshotMapper;

    public SafetyStatusSnapshotRepositoryImpl(SafetyStatusSnapshotMapper safetyStatusSnapshotMapper) {
        this.safetyStatusSnapshotMapper = safetyStatusSnapshotMapper;
    }

    // ===================== 讀取 =====================

    /**
     * 依 pointId 讀取一筆快照。
     * 若你的 Entity 已將 pointId 標註為 @TableId，selectById 會直接以 PK 查詢。
     */
    @Override
    public Optional<SafetyStatusSnapshot> findByPointId(Long pointId) {
        return Optional.ofNullable(safetyStatusSnapshotMapper.selectById(pointId));
    }

    /**
     * 查詢全部快照。
     */
    @Override
    public List<SafetyStatusSnapshot> findAll() {
        return safetyStatusSnapshotMapper.selectList(new QueryWrapper<>());
    }

    /**
     * 依多個 pointId 批次查詢快照。
     */
    @Override
    public List<SafetyStatusSnapshot> findAllByPointIds(List<Long> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return Collections.emptyList();
        }
        return safetyStatusSnapshotMapper.selectList(
                new QueryWrapper<SafetyStatusSnapshot>().in("point_id", pointIds)
        );
    }

    /**
     * 回傳資料總筆數。
     */
    @Override
    public long count() {
        // MyBatis-Plus 的 selectCount 回傳 Long
        return safetyStatusSnapshotMapper.selectCount(null);
    }

    // ===================== 新增 / 更新 =====================

    /**
     * 新增單筆資料。
     */
    @Override
    public boolean save(SafetyStatusSnapshot entity) {
        return safetyStatusSnapshotMapper.insert(entity) > 0;
    }

    /**
     * 批次新增（簡單 for 迴圈版本；如需更高效可改用自訂 Mapper 的 batchInsert）。
     */
    @Override
    public boolean saveBatch(List<SafetyStatusSnapshot> entities) {
        if (entities == null || entities.isEmpty()) return true;
        int affected = 0;
        for (SafetyStatusSnapshot e : entities) {
            affected += safetyStatusSnapshotMapper.insert(e);
        }
        return affected == entities.size();
    }

    /**
     * 新增或更新（存在則 update，不存在則 insert）。
     * 若你需要原子語意可改成自訂 Mapper 使用
     * INSERT ... ON DUPLICATE KEY UPDATE。
     */
    @Override
    public boolean upsert(SafetyStatusSnapshot entity) {
        if (entity == null || entity.getPointId() == null) {
            return false;
        }
        SafetyStatusSnapshot existed = safetyStatusSnapshotMapper.selectById(entity.getPointId());
        if (existed == null) {
            return safetyStatusSnapshotMapper.insert(entity) > 0;
        } else {
            return safetyStatusSnapshotMapper.updateById(entity) > 0;
        }
    }

    /**
     * 依主鍵更新整筆資料。
     */
    @Override
    public boolean update(SafetyStatusSnapshot entity) {
        return safetyStatusSnapshotMapper.updateById(entity) > 0;
    }

    /**
     * 只更新 is_triggered / last_change_time / last_poll_time 三個欄位。
     * - is_triggered：以 'Y' / 'N' 存入
     * - last_change_time：狀態變化當下時間
     * - last_poll_time：每次輪詢更新
     */
    @Override
    public boolean updateTriggerAndTimes(Long pointId,
                                         boolean triggered,
                                         LocalDateTime lastChangeTime,
                                         LocalDateTime lastPollTime) {
        if (pointId == null) return false;

        UpdateWrapper<SafetyStatusSnapshot> uw = new UpdateWrapper<>();
        uw.eq("point_id", pointId)
                .set("is_triggered", triggered ? "Y" : "N")
                .set("last_change_time", lastChangeTime)
                .set("last_poll_time", lastPollTime);

        return safetyStatusSnapshotMapper.update(null, uw) > 0;
    }

    // ===================== 刪除 =====================

    /**
     * 依 pointId 刪除。
     */
    @Override
    public boolean deleteByPointId(Long pointId) {
        if (pointId == null) return false;
        return safetyStatusSnapshotMapper.deleteById(pointId) > 0;
    }
}
