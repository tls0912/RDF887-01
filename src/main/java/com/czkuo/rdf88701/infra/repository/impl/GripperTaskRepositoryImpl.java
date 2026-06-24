package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.application.dto.query.GripperTaskQuery;
import com.czkuo.rdf88701.application.service.History.GripperTaskHistoryInsertService;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.common.util.LambdaQueryHelper;
import com.czkuo.rdf88701.domain.repository.GripperTaskRepository;
import com.czkuo.rdf88701.infra.entity.GripperTask;
import com.czkuo.rdf88701.infra.mapper.GripperTaskHistoryMapper;
import com.czkuo.rdf88701.infra.mapper.GripperTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * GripperTask 資料存取實作
 * - 提供 Gripper 任務的 CRUD、狀態更新、歷史歸檔、條件查詢等功能
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
@RequiredArgsConstructor
public class GripperTaskRepositoryImpl implements GripperTaskRepository {

    private final GripperTaskMapper gripperTaskMapper;
    private final GripperTaskHistoryMapper gripperTaskHistoryMapper;
    private final GripperTaskHistoryInsertService gripperTaskHistoryInsertService;

    /**
     * 根據 ID 查詢單筆任務
     */
    @Override
    public Optional<GripperTask> findById(Long id) {
        return Optional.ofNullable(gripperTaskMapper.selectById(id));
    }

    /**
     * 查詢所有任務（不建議用於大量資料）
     */
    @Override
    public List<GripperTask> findAll() {
        return gripperTaskMapper.selectList(null);
    }

    /**
     * 查詢指定容器的最新任務
     * - 依 created_time DESC 排序取第一筆
     */
    @Override
    public Optional<GripperTask> findLatestByContainerMainId(Long containerMainId) {
        return Optional.ofNullable(
                gripperTaskMapper.selectOne(
                        new LambdaQueryHelper<GripperTask>().getWrapper()
                                .eq(GripperTask::getContainerMainId, containerMainId)
                                .orderByDesc(GripperTask::getCreatedTime)
                                .last("LIMIT 1")
                )
        );
    }

    /**
     * 查詢指定容器的最新任務
     * - 依 created_time DESC 排序取第一筆
     */
    @Override
    public Optional<GripperTask> findLatestByContainerAndGripper(Long gripperId, Long containerMainId) {
        return Optional.ofNullable(
                gripperTaskMapper.selectOne(
                        new LambdaQueryHelper<GripperTask>().getWrapper()
                                .eq(GripperTask::getGripperId, gripperId)
                                .eq(GripperTask::getContainerMainId, containerMainId)
                                .orderByDesc(GripperTask::getCreatedTime)
                                .last("LIMIT 1")
                )
        );
    }

    /**
     * 多條件查詢（不分頁）
     */
    @Override
    public List<GripperTask> findByCondition(GripperTaskQuery query) {
        LambdaQueryHelper<GripperTask> helper = buildQueryWrapper(query);
        int offset = (query.getPageNum() - 1) * query.getPageSize();
        helper.getWrapper().last("LIMIT " + offset + "," + query.getPageSize());
        return gripperTaskMapper.selectList(helper.getWrapper());
    }

    /**
     * 多條件查詢（分頁）
     */
    @Override
    public PageResult<GripperTask> findPageByCondition(GripperTaskQuery query) {
        LambdaQueryHelper<GripperTask> helper = buildQueryWrapper(query);
        int pageNum = query.getSafePageNum();
        int pageSize = query.getSafePageSize();
        Page<GripperTask> page = new Page<>(pageNum, pageSize);
        IPage<GripperTask> result = gripperTaskMapper.selectPage(page, helper.getWrapper());
        return new PageResult<>(pageNum, pageSize, result.getTotal(), result.getRecords());
    }

    /**
     * 建立查詢條件包裝器
     */
    private LambdaQueryHelper<GripperTask> buildQueryWrapper(GripperTaskQuery query) {
        LambdaQueryHelper<GripperTask> helper = LambdaQueryHelper.<GripperTask>of()
                .eqIfPresent(GripperTask::getId, query::getId)
                .eqIfPresent(GripperTask::getRequestId, query::getRequestId)
                .eqIfPresent(GripperTask::getRequestVersion, query::getRequestVersion)
                .eqIfPresent(GripperTask::getGripperId, query::getGripperId)
                .eqIfPresent(GripperTask::getTaskType, query::getTaskType)
                .eqIfPresent(GripperTask::getTaskStatus, query::getTaskStatus)
                .eqIfPresent(GripperTask::getContainerMainId, query::getContainerMainId)
                .eqIfPresent(GripperTask::getFromLocationId, query::getSourceLocationId)
                .eqIfPresent(GripperTask::getToLocationId, query::getTargetLocationId)
                .eqIfPresent(GripperTask::getTargetHeightMm, query::getTargetHeightMm)
                .eqIfPresent(GripperTask::getLayerCount, query::getLayerCount)
                .geIfPresent(GripperTask::getDispatchedTime, query::getDispatchedAfter)
                .leIfPresent(GripperTask::getDispatchedTime, query::getDispatchedBefore)
                .geIfPresent(GripperTask::getCompletedTime, query::getCompletedAfter)
                .leIfPresent(GripperTask::getCompletedTime, query::getCompletedBefore)
                .geIfPresent(GripperTask::getCancelledTime, query::getCancelledAfter)
                .leIfPresent(GripperTask::getCancelledTime, query::getCancelledBefore)
                .likeIfPresent(GripperTask::getOperator, query::getOperator)
                .geIfPresent(GripperTask::getCreatedTime, query::getCreatedAfter)
                .leIfPresent(GripperTask::getCreatedTime, query::getCreatedBefore)
                .geIfPresent(GripperTask::getUpdatedTime, query::getUpdatedAfter)
                .leIfPresent(GripperTask::getUpdatedTime, query::getUpdatedBefore);

        if (StringUtils.hasText(query.getKeyword())) {
            helper.getWrapper().and(w -> w
                    .like(GripperTask::getRemark, query.getKeyword())
                    .or()
                    .like(GripperTask::getCancelledReason, query.getKeyword())
            );
        }

        return helper;
    }

    /**
     * 新增任務，並寫入歷史紀錄
     */
    @Override
    public boolean save(GripperTask entity) {
        boolean inserted = gripperTaskMapper.insert(entity) > 0;
        if (inserted) archive(entity, "INSERT");
        return inserted;
    }

    /**
     * 更新任務，並寫入歷史紀錄
     */
    @Override
    public boolean update(GripperTask entity) {
        boolean updated = gripperTaskMapper.updateById(entity) > 0;
        if (updated) archive(entity, "UPDATE");
        return updated;
    }

    /**
     * 根據 ID 刪除任務，並寫入歷史紀錄
     */
    @Override
    public boolean deleteById(Long id) {
        GripperTask task = gripperTaskMapper.selectById(id);
        if (task != null) {
            archive(task, "DELETE");
            return gripperTaskMapper.deleteById(id) > 0;
        }
        return false;
    }

    /**
     * 更新任務狀態，並記錄歷史
     */
    @Override
    public boolean updateTaskStatus(Long id, String status) {
        GripperTask task = gripperTaskMapper.selectById(id);
        if (task != null) {
            task.setTaskStatus(status);
            task.setUpdatedTime(LocalDateTime.now());
            boolean updated = gripperTaskMapper.updateById(task) > 0;
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
     * 補寫 done_time（不影響任務狀態），並記錄歷史
     */
    @Override
    public boolean markTaskAsDone(Long id) {
        GripperTask task = gripperTaskMapper.selectById(id);
        if (task != null) {
            task.setDoneTime(LocalDateTime.now());
            task.setUpdatedTime(LocalDateTime.now());
            boolean updated = gripperTaskMapper.updateById(task) > 0;
            if (updated) archive(task, "UPDATE");
            return updated;
        }
        return false;
    }

    /**
     * 檢查指定 Gripper 是否存在尚未完成的任務
     * - 排除：COMPLETED、FAILED、CANCELLED、SKIPPED
     * - 或為已完成但未補寫 done_time 亦視為未完成
     */
    @Override
    public boolean existsUnfinishedTaskForGripper(Long gripperId) {
        return gripperTaskMapper.selectCount(
                new LambdaQueryHelper<GripperTask>().getWrapper()
                        .eq(GripperTask::getGripperId, gripperId)
                        .and(wrapper -> wrapper
                                .notIn(GripperTask::getTaskStatus, "COMPLETED", "FAILED", "CANCELLED", "SKIPPED")
                                .or(inner -> inner
                                        .in(GripperTask::getTaskStatus, "COMPLETED", "FAILED")
                                        .isNull(GripperTask::getDoneTime)
                                )
                        )
        ) > 0;
    }

    /**
     * 查詢指定 Gripper 裝置當前最優先任務（DISPATCHED > PENDING）
     * - 排序依據：status、priority_level DESC、created_time ASC
     */
    @Override
    public Optional<GripperTask> findTopTaskByGripperOrdered(int gripperId) {
        return Optional.ofNullable(gripperTaskMapper.findTopTaskByGripperOrdered(gripperId));
    }

    /**
     * 查詢該容器在此 Gripper 上最後一筆有效 PICK 任務
     * - 以 id 倒序排序（代表最新）
     */
    @Override
    public Optional<GripperTask> findLastPickTaskByContainer(Long gripperId, Long containerMainId) {
        return Optional.ofNullable(gripperTaskMapper.findLastPickTaskByContainer(gripperId, containerMainId));
    }

    /**
     * CAS：原子性更新 container_main_id（若且唯若當前值等於期望舊值，且任務未結束）
     */
    @Override
    public boolean updateContainerMainIdIfUnchanged(Long taskId,
                                                    Long expectedOldContainerId,
                                                    Long newContainerId) {
        if (taskId == null || expectedOldContainerId == null || newContainerId == null) {
            return false;
        }

        LambdaUpdateWrapper<GripperTask> uw = new LambdaUpdateWrapper<>();
        uw.eq(GripperTask::getId, taskId)
                .eq(GripperTask::getContainerMainId, expectedOldContainerId)
                .notIn(GripperTask::getTaskStatus, "COMPLETED", "FAILED", "CANCELLED", "SKIPPED")
                .set(GripperTask::getContainerMainId, newContainerId)
                .set(GripperTask::getUpdatedTime, LocalDateTime.now());

        int rows = gripperTaskMapper.update(null, uw);
        if (rows == 1) {
            // 成功後補一筆歷史（讀最新狀態再歸檔）
            GripperTask updated = gripperTaskMapper.selectById(taskId);
            if (updated != null) {
                archive(updated, "UPDATE");
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean existsUnfinishedTaskForGripperToTargetAndType(Long gripperId, Long targetLocationId, String taskType) {
        return gripperTaskMapper.selectCount(
                new LambdaQueryHelper<GripperTask>().getWrapper()
                        .eq(GripperTask::getGripperId, gripperId)
                        .eq(GripperTask::getToLocationId, targetLocationId)
                        .eq(GripperTask::getTaskType, taskType)
                        .and(w -> w
                                .notIn(GripperTask::getTaskStatus, "COMPLETED", "FAILED", "CANCELLED", "SKIPPED")
                                .or(inner -> inner
                                        .in(GripperTask::getTaskStatus, "COMPLETED", "FAILED")
                                        .isNull(GripperTask::getDoneTime)
                                )
                        )
        ) > 0;
    }

    /**
     * 歷史歸檔（寫入 gripper_task_history 表）
     *
     * @param task       原始任務資料
     * @param changeType 操作類型（INSERT / UPDATE / DELETE）
     */
    private void archive(GripperTask task, String changeType) {
        gripperTaskHistoryInsertService.offer(task, changeType);
//        GripperTaskHistory history = new GripperTaskHistory();
//        BeanUtils.copyProperties(task, history, "id");
//        history.setOriginId(task.getId());
//        history.setChangeType(changeType);
//        history.setArchivedTime(LocalDateTime.now());
//        gripperTaskHistoryMapper.insert(history);
    }
}
