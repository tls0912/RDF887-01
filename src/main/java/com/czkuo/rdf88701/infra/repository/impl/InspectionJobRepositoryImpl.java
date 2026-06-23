package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.czkuo.rdf88701.domain.repository.InspectionJobRepository;
import com.czkuo.rdf88701.infra.entity.InspectionJob;
import com.czkuo.rdf88701.infra.mapper.InspectionJobMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus 實作說明：
 *  - 一律用 LambdaQueryWrapper / LambdaUpdateWrapper，避免手寫欄位名（比較安全）。
 *  - 所有更新都加上 isClosed=false 的條件，確保只變更進行中的任務。
 *  - 時間欄位 updatedTime 每次更新都會寫入 LocalDateTime.now()。
 *
 * 注意：
 *  - 若你的 InspectionJob.isClosed 是 Integer(0/1) 而不是 Boolean，請將 eq(..., false) 改成 eq(..., 0)；
 *    set(..., true) 改成 set(..., 1)（本實作假設 isClosed 綁定 Boolean）。
 */
@Repository
public class InspectionJobRepositoryImpl implements InspectionJobRepository {

    private final InspectionJobMapper inspectionJobMapper;

    public InspectionJobRepositoryImpl(InspectionJobMapper inspectionJobMapper) {
        this.inspectionJobMapper = inspectionJobMapper;
    }

    // ===== 基本 CRUD =====

    @Override
    public Optional<InspectionJob> findById(Long id) {
        return Optional.ofNullable(inspectionJobMapper.selectById(id));
    }

    @Override
    public boolean save(InspectionJob entity) {
        // 補時間戳（若你有 MyBatis-Plus 自動填充，可移除此段）
        LocalDateTime now = LocalDateTime.now();
        if (entity.getCreatedTime() == null) entity.setCreatedTime(now);
        entity.setUpdatedTime(now);
        return inspectionJobMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(InspectionJob entity) {
        entity.setUpdatedTime(LocalDateTime.now());
        return inspectionJobMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return inspectionJobMapper.deleteById(id) > 0;
    }

    @Override
    public List<InspectionJob> findAll() {
        return inspectionJobMapper.selectList(Wrappers.<InspectionJob>lambdaQuery());
    }

    // ===== 進行中（未關閉）任務查詢 =====

    @Override
    public List<InspectionJob> findActiveJobs() {
        LambdaQueryWrapper<InspectionJob> q = Wrappers.<InspectionJob>lambdaQuery()
                .eq(InspectionJob::getIsClosed, false)
                .orderByAsc(InspectionJob::getCreatedTime)
                .orderByAsc(InspectionJob::getId);
        return inspectionJobMapper.selectList(q);
    }

    @Override
    public boolean existsActiveForGripper(Long gripperId) {
        LambdaQueryWrapper<InspectionJob> q = Wrappers.<InspectionJob>lambdaQuery()
                .eq(InspectionJob::getGripperId, gripperId)
                .eq(InspectionJob::getIsClosed, false);
        Long cnt = inspectionJobMapper.selectCount(q);
        return cnt != null && cnt > 0;
    }

    @Override
    public Optional<InspectionJob> findActiveByGripper(Long gripperId) {
        LambdaQueryWrapper<InspectionJob> q = Wrappers.<InspectionJob>lambdaQuery()
                .eq(InspectionJob::getGripperId, gripperId)
                .eq(InspectionJob::getIsClosed, false)
                .last("LIMIT 1");
        return Optional.ofNullable(inspectionJobMapper.selectOne(q));
    }

    // ===== 狀態更新／補關 =====

    @Override
    public boolean markStatus(Long jobId, String status) {
        LambdaUpdateWrapper<InspectionJob> u = Wrappers.<InspectionJob>lambdaUpdate()
                .eq(InspectionJob::getId, jobId)
                .eq(InspectionJob::getIsClosed, false)
                .set(InspectionJob::getStatus, status)
                .set(InspectionJob::getUpdatedTime, LocalDateTime.now());
        return inspectionJobMapper.update(null, u) > 0;
    }

    @Override
    public boolean markStatusIfIn(Long jobId, String status, List<String> expectedCurrentStatuses) {
        LambdaUpdateWrapper<InspectionJob> u = Wrappers.<InspectionJob>lambdaUpdate()
                .eq(InspectionJob::getId, jobId)
                .eq(InspectionJob::getIsClosed, false)
                .in(expectedCurrentStatuses != null && !expectedCurrentStatuses.isEmpty(),
                        InspectionJob::getStatus, expectedCurrentStatuses)
                .set(InspectionJob::getStatus, status)
                .set(InspectionJob::getUpdatedTime, LocalDateTime.now());
        return inspectionJobMapper.update(null, u) > 0;
    }

    @Override
    public boolean markSecondDoneAndCloseIfActive(Long jobId) {
        // 原子補關：只在 is_closed=0 時才寫入 SECOND_DONE 並關閉
        LambdaUpdateWrapper<InspectionJob> u = Wrappers.<InspectionJob>lambdaUpdate()
                .eq(InspectionJob::getId, jobId)
                .eq(InspectionJob::getIsClosed, false)
                .set(InspectionJob::getStatus, "SECOND_DONE")
                .set(InspectionJob::getIsClosed, true)
                .set(InspectionJob::getFailReason, null)
                .set(InspectionJob::getUpdatedTime, LocalDateTime.now());
        return inspectionJobMapper.update(null, u) > 0;
    }

    @Override
    public boolean closeAsDone(Long jobId) {
        LambdaUpdateWrapper<InspectionJob> u = Wrappers.<InspectionJob>lambdaUpdate()
                .eq(InspectionJob::getId, jobId)
                .eq(InspectionJob::getIsClosed, false)
                .set(InspectionJob::getStatus, "DONE")
                .set(InspectionJob::getIsClosed, true)
                .set(InspectionJob::getFailReason, null)
                .set(InspectionJob::getUpdatedTime, LocalDateTime.now());
        return inspectionJobMapper.update(null, u) > 0;
    }

    @Override
    public boolean fail(Long jobId, String reason) {
        LambdaUpdateWrapper<InspectionJob> u = Wrappers.<InspectionJob>lambdaUpdate()
                .eq(InspectionJob::getId, jobId)
                .eq(InspectionJob::getIsClosed, false)
                .set(InspectionJob::getStatus, "FAILED")
                .set(InspectionJob::getIsClosed, true)
                .set(InspectionJob::getFailReason, truncate(reason, 240))
                .set(InspectionJob::getUpdatedTime, LocalDateTime.now());
        return inspectionJobMapper.update(null, u) > 0;
    }

    // ===== Utils =====

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
