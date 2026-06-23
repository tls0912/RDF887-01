package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.application.dto.query.WorkingBeamTaskQuery;
import com.czkuo.rdf88701.application.service.History.WorkingBeamTaskHistoryInsertService;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.common.util.LambdaQueryHelper;
import com.czkuo.rdf88701.domain.repository.WorkingBeamTaskRepository;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;
import com.czkuo.rdf88701.infra.mapper.WorkingBeamTaskHistoryMapper;
import com.czkuo.rdf88701.infra.mapper.WorkingBeamTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * WorkingBeamTask 資料存取實作
 */
@Repository
@RequiredArgsConstructor
public class WorkingBeamTaskRepositoryImpl implements WorkingBeamTaskRepository {

    private final WorkingBeamTaskMapper workingBeamTaskMapper;
    private final WorkingBeamTaskHistoryMapper workingBeamTaskHistoryMapper;
    private final WorkingBeamTaskHistoryInsertService workingBeamTaskHistoryInsertService;

    /**
     * 根據 ID 查詢任務
     */
    @Override
    public Optional<WorkingBeamTask> findById(Long id) {
        return Optional.ofNullable(workingBeamTaskMapper.selectById(id));
    }

    /**
     * 新增任務並備份歷史紀錄
     */
    @Override
    public boolean save(WorkingBeamTask entity) {
        boolean inserted = workingBeamTaskMapper.insert(entity) > 0;
        if (inserted) archive(entity, "INSERT");
        return inserted;
    }

    /**
     * 更新任務並備份歷史紀錄
     */
    @Override
    public boolean update(WorkingBeamTask entity) {
        boolean updated = workingBeamTaskMapper.updateById(entity) > 0;
        if (updated) archive(entity, "UPDATE");
        return updated;
    }

    /**
     * 刪除任務並備份歷史紀錄（保留刪除前資料）
     */
    @Override
    public boolean deleteById(Long id) {
        WorkingBeamTask task = workingBeamTaskMapper.selectById(id);
        if (task != null) {
            archive(task, "DELETE");
            return workingBeamTaskMapper.deleteById(id) > 0;
        }
        return false;
    }

    /**
     * 查詢全部任務
     */
    @Override
    public List<WorkingBeamTask> findAll() {
        return workingBeamTaskMapper.selectList(null);
    }

    /**
     * 查詢符合條件的任務清單（不分頁）
     */
    @Override
    public List<WorkingBeamTask> findByCondition(WorkingBeamTaskQuery query) {
        return workingBeamTaskMapper.selectList(buildQueryWrapper(query).getWrapper());
    }

    /**
     * 查詢符合條件的任務清單（分頁）
     */
    @Override
    public PageResult<WorkingBeamTask> findPageByCondition(WorkingBeamTaskQuery query) {
        Page<WorkingBeamTask> page = new Page<>(query.getSafePageNum(), query.getSafePageSize());
        IPage<WorkingBeamTask> result = workingBeamTaskMapper.selectPage(page, buildQueryWrapper(query).getWrapper());
        return new PageResult<>(query.getSafePageNum(), query.getSafePageSize(), result.getTotal(), result.getRecords());
    }

    /**
     * 更新任務狀態（並寫入歷史）
     */
    @Override
    public boolean updateTaskStatus(Long id, String status) {
        WorkingBeamTask task = workingBeamTaskMapper.selectById(id);
        if (task != null) {
            task.setTaskStatus(status);
            task.setUpdatedTime(LocalDateTime.now());
            boolean updated = workingBeamTaskMapper.updateById(task) > 0;
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
     * 標記任務為已結束（補寫 DoneTime 並寫入歷史）
     */
    @Override
    public boolean markTaskAsDone(Long id) {
        WorkingBeamTask task = workingBeamTaskMapper.selectById(id);
        if (task != null) {
            task.setDoneTime(LocalDateTime.now());
            task.setUpdatedTime(LocalDateTime.now());
            boolean updated = workingBeamTaskMapper.updateById(task) > 0;
            if (updated) archive(task, "UPDATE");
            return updated;
        }
        return false;
    }

    /**
     * 建立查詢條件（支援條件為空跳過）
     */
    private LambdaQueryHelper<WorkingBeamTask> buildQueryWrapper(WorkingBeamTaskQuery query) {
        return LambdaQueryHelper.<WorkingBeamTask>of()
                .eqIfPresent(WorkingBeamTask::getRequestId, query::getRequestId)
                .eqIfPresent(WorkingBeamTask::getWorkingBeamId, query::getWorkingBeamId)
                .eqIfPresent(WorkingBeamTask::getDirection, query::getDirection)
                .eqIfPresent(WorkingBeamTask::getTaskStatus, query::getTaskStatus)
                .eqIfPresent(WorkingBeamTask::getPriorityLevel, query::getPriorityLevel)
                .geIfPresent(WorkingBeamTask::getDispatchedTime, query::getDispatchedAfter)
                .leIfPresent(WorkingBeamTask::getDispatchedTime, query::getDispatchedBefore)
                .geIfPresent(WorkingBeamTask::getCompletedTime, query::getCompletedAfter)
                .leIfPresent(WorkingBeamTask::getCompletedTime, query::getCompletedBefore)
                .geIfPresent(WorkingBeamTask::getCancelledTime, query::getCancelledAfter)
                .leIfPresent(WorkingBeamTask::getCancelledTime, query::getCancelledBefore);
    }

    /**
     * 寫入 working_beam_task_history 歷史紀錄
     *
     * @param task       原始任務資料
     * @param changeType 操作類型（INSERT / UPDATE / DELETE）
     */
    private void archive(WorkingBeamTask task, String changeType) {
        workingBeamTaskHistoryInsertService.offer(task,changeType);
//        WorkingBeamTaskHistory history = new WorkingBeamTaskHistory();
//        BeanUtils.copyProperties(task, history, "id");
//        history.setOriginId(task.getId());
//        history.setChangeType(changeType);
//        history.setArchivedTime(LocalDateTime.now());
//        workingBeamTaskHistoryMapper.insert(history);
    }

    /**
     * 檢查指定 WorkingBeam 是否存在尚未完成的任務
     * <p>
     * - 用於排程器或任務分派器判斷是否可派發新任務
     * - 排除狀態：COMPLETED、FAILED、CANCELLED、SKIPPED
     *
     * @param workingBeamId Working Beam 裝置 ID
     * @return true：存在尚未完成任務；false：任務皆已結束
     */
    @Override
    public boolean existsUnfinishedTaskForBeam(Long workingBeamId) {
        return workingBeamTaskMapper.selectCount(
                new LambdaQueryHelper<WorkingBeamTask>().getWrapper()
                        .eq(WorkingBeamTask::getWorkingBeamId, workingBeamId)
                        // 狀態不是完成類型 → 一律視為未完成
                        .notIn(WorkingBeamTask::getTaskStatus, "COMPLETED", "FAILED", "CANCELLED", "SKIPPED")
                        // 或是 COMPLETED / FAILED，但沒有 done_time → 未完成
                        .or(inner -> inner
                                .in(WorkingBeamTask::getTaskStatus, "COMPLETED", "FAILED")
                                .isNull(WorkingBeamTask::getDoneTime)
                        )
        ) > 0;
    }

    /**
     * 查詢指定 WorkingBeam 裝置當前最優先任務（交由 Mapper 處理排序）
     * - 僅包含尚未 done 的任務
     * - 僅篩選狀態為：DISPATCHED 或 PENDING
     * - 排序依據：DISPATCHED > PENDING、priority_level DESC、created_time ASC
     */
    @Override
    public Optional<WorkingBeamTask> findTopTaskByWorkingBeamOrdered(int workingBeamId) {
        return Optional.ofNullable(workingBeamTaskMapper.findTopTaskByWorkingBeamOrdered(workingBeamId));
    }
}
