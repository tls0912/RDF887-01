package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.application.dto.query.CraneTaskQuery;
import com.czkuo.rdf88701.application.service.History.CraneTaskHistoryInsertService;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.common.util.LambdaQueryHelper;
import com.czkuo.rdf88701.domain.repository.CraneTaskRepository;
import com.czkuo.rdf88701.infra.entity.CraneTask;
import com.czkuo.rdf88701.infra.entity.CraneTaskHistory;
import com.czkuo.rdf88701.infra.mapper.CraneTaskHistoryMapper;
import com.czkuo.rdf88701.infra.mapper.CraneTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * CraneTask 資料存取實作
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
@RequiredArgsConstructor
public class CraneTaskRepositoryImpl implements CraneTaskRepository {

    private final CraneTaskMapper craneTaskMapper;
    private final CraneTaskHistoryMapper craneTaskHistoryMapper;
    private final CraneTaskHistoryInsertService craneTaskHistoryInsertService;

    @Override
    public Optional<CraneTask> findById(Long id) {
        return Optional.ofNullable(craneTaskMapper.selectById(id));
    }

    /**
     * 查詢指定天車的待派工任務清單（僅 PENDING 狀態）
     */
    @Override
    public List<CraneTask> findPendingTasksByCraneId(String craneId) {
        return craneTaskMapper.selectList(new LambdaQueryWrapper<CraneTask>()
                .eq(CraneTask::getCraneId, craneId)
                .eq(CraneTask::getTaskStatus, "PENDING"));
    }

    /**
     * 查詢指定天車下一筆應執行的任務（以優先級升序排序）
     */
    @Override
    public Optional<CraneTask> findNextPendingTask(String craneId) {
        return Optional.ofNullable(craneTaskMapper.selectOne(new LambdaQueryWrapper<CraneTask>()
                .eq(CraneTask::getCraneId, craneId)
                .eq(CraneTask::getTaskStatus, "PENDING")
                .orderByAsc(CraneTask::getPriorityLevel)
                .last("LIMIT 1")));
    }

    /**
     * 取得天車目前任務（自訂條件，如尚未完成中最早建立的）
     */
    @Override
    public Optional<CraneTask> findTopTaskByCraneOrdered(int craneId) {
        return Optional.ofNullable(craneTaskMapper.selectTopTaskByCrane(craneId));
    }

    /**
     * 查詢指定容器的最新任務
     * - 依 created_time DESC 排序取第一筆
     */
    @Override
    public Optional<CraneTask> findLatestByContainerMainId(Long containerMainId) {
        return Optional.ofNullable(craneTaskMapper.selectOne(
                new LambdaQueryWrapper<CraneTask>()
                        .eq(CraneTask::getContainerMainId, containerMainId)
                        .orderByDesc(CraneTask::getCreatedTime)
                        .last("LIMIT 1")
        ));
    }

    /**
     * 判斷天車是否存在尚未完成的任務
     */
    @Override
    public boolean existsUnfinishedTaskForCrane(String craneId) {
        return craneTaskMapper.selectCount(new LambdaQueryWrapper<CraneTask>()
                .eq(CraneTask::getCraneId, craneId)
                .and(wrapper -> wrapper
                        // 狀態不是完成類型 → 一律視為未完成
                        .notIn(CraneTask::getTaskStatus, "COMPLETED", "FAILED", "CANCELLED", "SKIPPED")
                        // 或是 COMPLETED / FAILED，但沒有 done_time → 未完成
                        .or(inner -> inner
                                .in(CraneTask::getTaskStatus, "COMPLETED", "FAILED")
                                .isNull(CraneTask::getDoneTime)
                        )
                )
        ) > 0;
    }

    /**
     * 查詢符合條件的任務清單（不分頁）
     */
    @Override
    public List<CraneTask> findByCondition(CraneTaskQuery query) {
        return craneTaskMapper.selectList(buildQueryWrapper(query).getWrapper());
    }

    /**
     * 查詢符合條件的任務清單（分頁）
     */
    @Override
    public PageResult<CraneTask> findPageByCondition(CraneTaskQuery query) {
        Page<CraneTask> page = new Page<>(query.getSafePageNum(), query.getSafePageSize());
        IPage<CraneTask> result = craneTaskMapper.selectPage(page, buildQueryWrapper(query).getWrapper());
        return new PageResult<>(query.getSafePageNum(), query.getSafePageSize(), result.getTotal(), result.getRecords());
    }

    /**
     * 新增任務並寫入歷史紀錄
     */
    @Override
    public boolean save(CraneTask entity) {
        boolean inserted = craneTaskMapper.insert(entity) > 0;
        if (inserted) archive(entity, "INSERT");
        return inserted;
    }

    /**
     * 更新任務並寫入歷史紀錄
     */
    @Override
    public boolean update(CraneTask entity) {
        boolean updated = craneTaskMapper.updateById(entity) > 0;
        if (updated) archive(entity, "UPDATE");
        return updated;
    }

    /**
     * 刪除任務並備份歷史紀錄（保留刪除前資料）
     */
    @Override
    public boolean deleteById(Long id) {
        CraneTask task = craneTaskMapper.selectById(id);
        if (task != null) {
            archive(task, "DELETE");
            return craneTaskMapper.deleteById(id) > 0;
        }
        return false;
    }

    /**
     * 更新任務狀態（並寫入歷史）
     */
    @Override
    public boolean updateTaskStatus(Long id, String status) {
        CraneTask task = craneTaskMapper.selectById(id);
        if (task != null) {
            task.setTaskStatus(status);
            task.setUpdatedTime(LocalDateTime.now());
            boolean updated = craneTaskMapper.updateById(task) > 0;
            if (updated) archive(task, "UPDATE");
            return updated;
        }
        return false;
    }

    /**
     * 標記為已派工
     */
    @Override
    public boolean markTaskAsDispatched(Long id) {
        return updateTaskStatus(id, "DISPATCHED");
    }

    /**
     * 標記為已完成
     */
    @Override
    public boolean markTaskAsCompleted(Long id) {
        return updateTaskStatus(id, "COMPLETED");
    }

    /**
     * 標記任務為已完成（補寫 DoneTime 並寫入歷史）
     */
    @Override
    public boolean markTaskAsDone(Long id) {
        CraneTask task = craneTaskMapper.selectById(id);
        if (task != null) {
            task.setDoneTime(LocalDateTime.now());
            task.setUpdatedTime(LocalDateTime.now());
            boolean updated = craneTaskMapper.updateById(task) > 0;
            if (updated) archive(task, "UPDATE");
            return updated;
        }
        return false;
    }

    /**
     * 查詢全部任務
     */
    @Override
    public List<CraneTask> findAll() {
        return craneTaskMapper.selectList(null);
    }

    /**
     * 查詢尚未完成（PENDING 或 DISPATCHED）的任務清單
     */
    @Override
    public List<CraneTask> findPendingOrDispatchedTasks() {
        return craneTaskMapper.selectList(new LambdaQueryWrapper<CraneTask>()
                .in(CraneTask::getTaskStatus, "PENDING", "DISPATCHED")
                .orderByDesc(CraneTask::getPriorityLevel)
                .orderByAsc(CraneTask::getCreatedTime));
    }

    /**
     * 指定來源位是否存在未完成 CraneTask（crane 將要去「取」該來源位）
     */
    @Override
    public boolean existsUnfinishedTaskPickFromLocation(String craneId, Long sourceLocationId) {
        if (craneId == null || craneId.isBlank() || sourceLocationId == null) return false;

        return craneTaskMapper.selectCount(
                new LambdaQueryHelper<CraneTask>().getWrapper()
                        .eq(CraneTask::getCraneId, craneId)
                        .eq(CraneTask::getSourceLocationId, sourceLocationId)
                        .and(w -> w
                                .notIn(CraneTask::getTaskStatus, "COMPLETED", "FAILED", "CANCELLED", "SKIPPED")
                                .or(inner -> inner
                                        .in(CraneTask::getTaskStatus, "COMPLETED", "FAILED")
                                        .isNull(CraneTask::getDoneTime)
                                )
                        )
        ) > 0;
    }

    /**
     * 指定目標位是否存在未完成 CraneTask（crane 將要去「放」到該目標位）
     */
    @Override
    public boolean existsUnfinishedTaskPlaceToLocation(String craneId, Long targetLocationId) {
        if (craneId == null || craneId.isBlank() || targetLocationId == null) return false;

        return craneTaskMapper.selectCount(
                new LambdaQueryHelper<CraneTask>().getWrapper()
                        .eq(CraneTask::getCraneId, craneId)
                        .eq(CraneTask::getTargetLocationId, targetLocationId)
                        .and(w -> w
                                .notIn(CraneTask::getTaskStatus, "COMPLETED", "FAILED", "CANCELLED", "SKIPPED")
                                .or(inner -> inner
                                        .in(CraneTask::getTaskStatus, "COMPLETED", "FAILED")
                                        .isNull(CraneTask::getDoneTime)
                                )
                        )
        ) > 0;
    }

    /**
     * 建立查詢條件（支援條件為空跳過）
     */
    private LambdaQueryHelper<CraneTask> buildQueryWrapper(CraneTaskQuery query) {
        return LambdaQueryHelper.<CraneTask>of()
                .eqIfPresent(CraneTask::getRequestId, query::getRequestId)
                .eqIfPresent(CraneTask::getCraneId, query::getCraneId)
                .eqIfPresent(CraneTask::getTaskType, query::getTaskType)
                .eqIfPresent(CraneTask::getTaskStatus, query::getTaskStatus)
                .eqIfPresent(CraneTask::getPriorityLevel, query::getPriorityLevel)
                .eqIfPresent(CraneTask::getContainerMainId, query::getContainerMainId)
                .eqIfPresent(CraneTask::getSourceLocationId, query::getSourceLocationId)
                .eqIfPresent(CraneTask::getTargetLocationId, query::getTargetLocationId)
                .geIfPresent(CraneTask::getDispatchedTime, query::getDispatchedAfter)
                .leIfPresent(CraneTask::getDispatchedTime, query::getDispatchedBefore)
                .geIfPresent(CraneTask::getCompletedTime, query::getCompletedAfter)
                .leIfPresent(CraneTask::getCompletedTime, query::getCompletedBefore)
                .geIfPresent(CraneTask::getCancelledTime, query::getCancelledAfter)
                .leIfPresent(CraneTask::getCancelledTime, query::getCancelledBefore);
    }

    /**
     * 寫入 crane_task_history 歷史紀錄
     *
     * @param task       原始任務資料
     * @param changeType 操作類型（INSERT / UPDATE / DELETE）
     */
    private void archive(CraneTask task, String changeType) {
        craneTaskHistoryInsertService.offer(task,changeType);

//        CraneTaskHistory history = new CraneTaskHistory();
//        BeanUtils.copyProperties(task, history, "id");
//        history.setOriginId(task.getId());
//        history.setChangeType(changeType);
//        history.setArchivedTime(LocalDateTime.now());
//        craneTaskHistoryMapper.insert(history);
    }
}
