package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.application.dto.query.InfraredTaskQuery;
import com.czkuo.rdf88701.application.service.History.InfraredTaskHistoryInsertService;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.common.util.LambdaQueryHelper;
import com.czkuo.rdf88701.domain.repository.InfraredTaskRepository;
import com.czkuo.rdf88701.infra.entity.InfraredTask;
import com.czkuo.rdf88701.infra.mapper.InfraredTaskHistoryMapper;
import com.czkuo.rdf88701.infra.mapper.InfraredTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * InfraredTask 資料存取實作
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
@RequiredArgsConstructor
public class InfraredTaskRepositoryImpl implements InfraredTaskRepository {

    private final InfraredTaskMapper infraredTaskMapper;
    private final InfraredTaskHistoryMapper infraredTaskHistoryMapper;
    private final InfraredTaskHistoryInsertService infraredTaskHistoryInsertService;

    /**
     * 根據 ID 查詢任務
     */
    @Override
    public Optional<InfraredTask> findById(Long id) {
        return Optional.ofNullable(infraredTaskMapper.selectById(id));
    }

    /**
     * 新增任務並備份歷史紀錄
     */
    @Override
    public boolean save(InfraredTask entity) {
        boolean inserted = infraredTaskMapper.insert(entity) > 0;
        if (inserted) archive(entity, "INSERT");
        return inserted;
    }

    /**
     * 更新任務並備份歷史紀錄
     */
    @Override
    public boolean update(InfraredTask entity) {
        boolean updated = infraredTaskMapper.updateById(entity) > 0;
        if (updated) archive(entity, "UPDATE");
        return updated;
    }

    /**
     * 刪除任務並備份歷史紀錄（保留刪除前資料）
     */
    @Override
    public boolean deleteById(Long id) {
        InfraredTask task = infraredTaskMapper.selectById(id);
        if (task != null) {
            archive(task, "DELETE");
            return infraredTaskMapper.deleteById(id) > 0;
        }
        return false;
    }

    /**
     * 查詢全部任務
     */
    @Override
    public List<InfraredTask> findAll() {
        return infraredTaskMapper.selectList(null);
    }

    /**
     * 查詢符合條件的任務清單（不分頁）
     */
    @Override
    public List<InfraredTask> findByCondition(InfraredTaskQuery query) {
        return infraredTaskMapper.selectList(buildQueryWrapper(query).getWrapper());
    }

    /**
     * 查詢符合條件的任務清單（分頁）
     */
    @Override
    public PageResult<InfraredTask> findPageByCondition(InfraredTaskQuery query) {
        Page<InfraredTask> page = new Page<>(query.getSafePageNum(), query.getSafePageSize());
        IPage<InfraredTask> result = infraredTaskMapper.selectPage(page, buildQueryWrapper(query).getWrapper());
        return new PageResult<>(query.getSafePageNum(), query.getSafePageSize(), result.getTotal(), result.getRecords());
    }

    /**
     * 更新任務狀態（並寫入歷史）
     */
    @Override
    public boolean updateTaskStatus(Long id, String status) {
        InfraredTask task = infraredTaskMapper.selectById(id);
        if (task != null) {
            task.setTaskStatus(status);
            task.setUpdatedTime(LocalDateTime.now());
            boolean updated = infraredTaskMapper.updateById(task) > 0;
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
        InfraredTask task = infraredTaskMapper.selectById(id);
        if (task != null) {
            task.setDoneTime(LocalDateTime.now());
            task.setUpdatedTime(LocalDateTime.now());
            boolean updated = infraredTaskMapper.updateById(task) > 0;
            if (updated) archive(task, "UPDATE");
            return updated;
        }
        return false;
    }

    /**
     * 建立查詢條件（支援條件為空跳過）
     */
    private LambdaQueryHelper<InfraredTask> buildQueryWrapper(InfraredTaskQuery query) {
        return LambdaQueryHelper.<InfraredTask>of()
                .eqIfPresent(InfraredTask::getRequestId, query::getRequestId)
                .eqIfPresent(InfraredTask::getInfraredId, query::getInfraredId)
                .eqIfPresent(InfraredTask::getTaskType, query::getTaskType)
                .eqIfPresent(InfraredTask::getTaskStatus, query::getTaskStatus)
                .eqIfPresent(InfraredTask::getPriorityLevel, query::getPriorityLevel)
                .geIfPresent(InfraredTask::getDispatchedTime, query::getDispatchedAfter)
                .leIfPresent(InfraredTask::getDispatchedTime, query::getDispatchedBefore)
                .geIfPresent(InfraredTask::getCompletedTime, query::getCompletedAfter)
                .leIfPresent(InfraredTask::getCompletedTime, query::getCompletedBefore)
                .geIfPresent(InfraredTask::getCancelledTime, query::getCancelledAfter)
                .leIfPresent(InfraredTask::getCancelledTime, query::getCancelledBefore);
    }

    /**
     * 寫入 infrared_task_history 歷史紀錄
     *
     * @param task       原始任務資料
     * @param changeType 操作類型（INSERT / UPDATE / DELETE）
     */
    private void archive(InfraredTask task, String changeType) {
        infraredTaskHistoryInsertService.offer(task,changeType);
//        InfraredTaskHistory history = new InfraredTaskHistory();
//        BeanUtils.copyProperties(task, history, "id");
//        history.setOriginId(task.getId());
//        history.setChangeType(changeType);
//        history.setArchivedTime(LocalDateTime.now());
//        infraredTaskHistoryMapper.insert(history);
    }

    /**
     * 檢查指定 Infrared 是否存在尚未完成的任務
     * <p>
     * - 用於排程器或任務分派器判斷是否可派發新任務
     * - 排除狀態：COMPLETED、FAILED、CANCELLED、SKIPPED
     *
     * @param infraredId Infrared 裝置 ID
     * @return true：存在尚未完成任務；false：任務皆已結束
     */
    @Override
    public boolean existsUnfinishedTaskForInfrared(Long infraredId) {
        return infraredTaskMapper.selectCount(
                new LambdaQueryHelper<InfraredTask>().getWrapper()
                        .eq(InfraredTask::getInfraredId, infraredId)
                        .notIn(InfraredTask::getTaskStatus, "COMPLETED", "FAILED", "CANCELLED", "SKIPPED")
                        .or(inner -> inner
                                .in(InfraredTask::getTaskStatus, "COMPLETED", "FAILED")
                                .isNull(InfraredTask::getDoneTime)
                        )
        ) > 0;
    }

    /**
     * 查詢指定 Infrared 裝置當前最優先任務（交由 Mapper 處理排序）
     * - 僅包含尚未 done 的任務
     * - 僅篩選狀態為：DISPATCHED 或 PENDING
     * - 排序依據：DISPATCHED > PENDING、priority_level DESC、created_time ASC
     */
    @Override
    public Optional<InfraredTask> findTopTaskByInfraredOrdered(int infraredId) {
        return Optional.ofNullable(infraredTaskMapper.findTopTaskByInfraredOrdered(infraredId));
    }
}
