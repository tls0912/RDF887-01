package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.application.dto.query.TransferTaskQuery;
import com.czkuo.rdf88701.application.service.History.TransferTaskHistoryInsertService;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.common.util.LambdaQueryHelper;
import com.czkuo.rdf88701.domain.repository.TransferTaskRepository;
import com.czkuo.rdf88701.infra.entity.TransferTask;
import com.czkuo.rdf88701.infra.mapper.TransferTaskHistoryMapper;
import com.czkuo.rdf88701.infra.mapper.TransferTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * TransferTask 資料存取實作
 * - 提供 Transfer 任務的 CRUD 與歷史歸檔功能
 * - 依據條件進行查詢與排序
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
@RequiredArgsConstructor
public class TransferTaskRepositoryImpl implements TransferTaskRepository {

    private final TransferTaskMapper transferTaskMapper;
    private final TransferTaskHistoryMapper transferTaskHistoryMapper;
    private final TransferTaskHistoryInsertService transferTaskHistoryInsertService;

    /**
     * 根據 ID 查詢單筆任務
     */
    @Override
    public Optional<TransferTask> findById(Long id) {
        return Optional.ofNullable(transferTaskMapper.selectById(id));
    }

    /**
     * 新增任務，並寫入歷史紀錄
     */
    @Override
    public boolean save(TransferTask entity) {
        boolean inserted = transferTaskMapper.insert(entity) > 0;
        if (inserted) archive(entity, "INSERT");
        return inserted;
    }

    /**
     * 更新任務，並寫入歷史紀錄
     */
    @Override
    public boolean update(TransferTask entity) {
        boolean updated = transferTaskMapper.updateById(entity) > 0;
        if (updated) archive(entity, "UPDATE");
        return updated;
    }

    /**
     * 刪除任務（先查出原始資料，寫入歷史紀錄）
     */
    @Override
    public boolean deleteById(Long id) {
        TransferTask task = transferTaskMapper.selectById(id);
        if (task != null) {
            archive(task, "DELETE");
            return transferTaskMapper.deleteById(id) > 0;
        }
        return false;
    }

    /**
     * 查詢所有 Transfer 任務（不建議用於大量資料）
     */
    @Override
    public List<TransferTask> findAll() {
        return transferTaskMapper.selectList(null);
    }

    /**
     * 查詢指定容器的最新任務
     * - 依 created_time DESC 排序取第一筆
     */
    @Override
    public Optional<TransferTask> findLatestByContainerMainId(Long containerMainId) {
        return Optional.ofNullable(
                transferTaskMapper.selectOne(
                        new QueryWrapper<TransferTask>()
                                .eq("container_main_id", containerMainId)
                                .orderByDesc("created_time")
                                .last("LIMIT 1")
                )
        );
    }

    /**
     * 查詢指定容器的最新任務
     * - 依 created_time DESC 排序取第一筆
     */
    @Override
    public Optional<TransferTask> findLatestByContainerAndTransfer(Long transferId, Long containerMainId) {
        return Optional.ofNullable(
                transferTaskMapper.selectOne(
                        new QueryWrapper<TransferTask>()
                                .eq("transfer_id", transferId)
                                .eq("container_main_id", containerMainId)
                                .orderByDesc("created_time")
                                .last("LIMIT 1")
                )
        );
    }

    /**
     * 多條件查詢（不分頁）
     */
    @Override
    public List<TransferTask> findByCondition(TransferTaskQuery query) {
        return transferTaskMapper.selectList(buildQueryWrapper(query).getWrapper());
    }

    /**
     * 多條件查詢（分頁）
     */
    @Override
    public PageResult<TransferTask> findPageByCondition(TransferTaskQuery query) {
        Page<TransferTask> page = new Page<>(query.getSafePageNum(), query.getSafePageSize());
        IPage<TransferTask> result = transferTaskMapper.selectPage(page, buildQueryWrapper(query).getWrapper());
        return new PageResult<>(query.getSafePageNum(), query.getSafePageSize(), result.getTotal(), result.getRecords());
    }

    /**
     * 更新任務狀態，並記錄歷史
     */
    @Override
    public boolean updateTaskStatus(Long id, String status) {
        TransferTask task = transferTaskMapper.selectById(id);
        if (task != null) {
            task.setTaskStatus(status);
            task.setUpdatedTime(LocalDateTime.now());
            boolean updated = transferTaskMapper.updateById(task) > 0;
            if (updated) archive(task, "UPDATE");
            return updated;
        }
        return false;
    }

    /**
     * 標記任務為 DISPATCHED
     */
    @Override
    public boolean markTaskAsDispatched(Long id) {
        return updateTaskStatus(id, "DISPATCHED");
    }

    /**
     * 標記任務為 COMPLETED
     */
    @Override
    public boolean markTaskAsCompleted(Long id) {
        return updateTaskStatus(id, "COMPLETED");
    }

    /**
     * 標記任務為已完成（補上 done_time）
     */
    @Override
    public boolean markTaskAsDone(Long id) {
        TransferTask task = transferTaskMapper.selectById(id);
        if (task != null) {
            task.setDoneTime(LocalDateTime.now());
            task.setUpdatedTime(LocalDateTime.now());
            boolean updated = transferTaskMapper.updateById(task) > 0;
            if (updated) archive(task, "UPDATE");
            return updated;
        }
        return false;
    }

    /**
     * 檢查指定 Transfer 裝置是否存在尚未完成的任務
     * - 排除狀態：COMPLETED, FAILED, CANCELLED, SKIPPED
     */
    @Override
    public boolean existsUnfinishedTaskForTransfer(Long transferId) {
        return transferTaskMapper.selectCount(
                new LambdaQueryHelper<TransferTask>().getWrapper()
                        .eq(TransferTask::getTransferId, transferId)
                        .and(wrapper -> wrapper
                                // 狀態不是完成類型 → 一律視為未完成
                                .notIn(TransferTask::getTaskStatus, "COMPLETED", "FAILED", "CANCELLED", "SKIPPED")
                                // 或是 COMPLETED / FAILED，但沒有 done_time → 未完成
                                .or(inner -> inner
                                        .in(TransferTask::getTaskStatus, "COMPLETED", "FAILED")
                                        .isNull(TransferTask::getDoneTime)
                                )
                        )
        ) > 0;
    }

    /**
     * 查詢 Transfer 裝置上的優先任務
     * - 僅篩選狀態為 DISPATCHED 或 PENDING
     * - 排序條件：DISPATCHED > PENDING、priority_level DESC、created_time ASC
     */
    @Override
    public Optional<TransferTask> findTopTaskByTransferOrdered(int transferId) {
        return Optional.ofNullable(transferTaskMapper.findTopTaskByTransferOrdered(transferId));
    }

    /**
     * 建立條件查詢包裝器
     */
    private LambdaQueryHelper<TransferTask> buildQueryWrapper(TransferTaskQuery query) {
        return LambdaQueryHelper.<TransferTask>of()
                .eqIfPresent(TransferTask::getRequestId, query::getRequestId)
                .eqIfPresent(TransferTask::getTransferId, query::getTransferId)
                .eqIfPresent(TransferTask::getTaskType, query::getTaskType)
                .eqIfPresent(TransferTask::getTaskStatus, query::getTaskStatus)
                .eqIfPresent(TransferTask::getPriorityLevel, query::getPriorityLevel)
                .geIfPresent(TransferTask::getDispatchedTime, query::getDispatchedAfter)
                .leIfPresent(TransferTask::getDispatchedTime, query::getDispatchedBefore)
                .geIfPresent(TransferTask::getCompletedTime, query::getCompletedAfter)
                .leIfPresent(TransferTask::getCompletedTime, query::getCompletedBefore)
                .geIfPresent(TransferTask::getCancelledTime, query::getCancelledAfter)
                .leIfPresent(TransferTask::getCancelledTime, query::getCancelledBefore);
    }

    /**
     * 寫入 transfer_task_history 歷史紀錄表
     *
     * @param task       原始任務資料
     * @param changeType 操作類型（INSERT / UPDATE / DELETE）
     */
    private void archive(TransferTask task, String changeType) {
        transferTaskHistoryInsertService.offer(task,changeType);
//        TransferTaskHistory history = new TransferTaskHistory();
//        BeanUtils.copyProperties(task, history, "id");
//        history.setOriginId(task.getId());
//        history.setChangeType(changeType);
//        history.setArchivedTime(LocalDateTime.now());
//        transferTaskHistoryMapper.insert(history);
    }

    /**
     * 查詢最近一筆 PICK 任務（根據 transferId 與 containerMainId）
     * - 排除已刪除
     * - 優先根據 created_time DESC 排序
     */
    @Override
    public Optional<TransferTask> findLastPickTaskByContainer(Long transferId, Long containerMainId) {
        return Optional.ofNullable(
                transferTaskMapper.selectOne(
                        new QueryWrapper<TransferTask>()
                                .eq("transfer_id", transferId)
                                .eq("container_main_id", containerMainId)
                                .eq("task_type", "PICK")
                                .orderByDesc("created_time")
                                .last("LIMIT 1")
                )
        );
    }
}
