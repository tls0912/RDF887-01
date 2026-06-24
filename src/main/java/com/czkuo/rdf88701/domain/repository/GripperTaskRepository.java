package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.application.dto.query.GripperTaskQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.infra.entity.GripperTask;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface GripperTaskRepository {

    /**
     * 查詢單筆
     */
    Optional<GripperTask> findById(Long id);

    /**
     * 多筆查詢（不分頁）
     */
    List<GripperTask> findByCondition(GripperTaskQuery query);

    /**
     * 分頁查詢（推薦用法）
     */
    PageResult<GripperTask> findPageByCondition(GripperTaskQuery query);

    /**
     * 新增任務
     */
    boolean save(GripperTask entity);

    /**
     * 更新任務（以 ID 為主）
     */
    boolean update(GripperTask entity);

    /**
     * 刪除任務
     */
    boolean deleteById(Long id);

    /**
     * 查詢全部 Gripper 任務（無條件）
     * 請小心使用，大量資料建議改用分頁
     */
    List<GripperTask> findAll();

    /**
     * 查詢單一 Gripper 的最高優先任務（DISPATCHED > PENDING, priority DESC）
     */
    Optional<GripperTask> findLatestByContainerMainId(Long containerMainId);

    /**
     * 取「指定容器 + 指定 Gripper」的最新一筆任務（不過濾狀態）
     * */
    Optional<GripperTask> findLatestByContainerAndGripper(Long gripperId, Long containerMainId);

    /**
     * 更新任務狀態
     */
    boolean updateTaskStatus(Long id, String status);

    /**
     * 標記指定任務為 DISPATCHED
     */
    boolean markTaskAsDispatched(Long id);

    /**
     * 標記指定任務為 COMPLETED
     */
    boolean markTaskAsCompleted(Long id);

    /**
     * 標記任務為結束（補上 done_time）
     */
    boolean markTaskAsDone(Long id);

    /**
     * 檢查是否存在尚未完成的任務（排除 COMPLETED / FAILED / CANCELLED / SKIPPED）
     */
    boolean existsUnfinishedTaskForGripper(Long gripperId);

    /**
     * 查詢指定 Gripper 裝置上優先處理的任務
     * 優先順序：DISPATCHED > PENDING，依 priority_level DESC
     */
    Optional<GripperTask> findTopTaskByGripperOrdered(int gripperId);

    /**
     * 根據容器 ID 查詢最後一筆有效 PICK 任務（目標是此 Gripper）
     */
    Optional<GripperTask> findLastPickTaskByContainer(Long gripperId, Long containerMainId);

    /**
     * 將任務的 container_main_id 從「預期的舊值」原子性地更新為「新值」。
     * 只有在目前 DB 中的 container_main_id == expectedOldContainerId，且任務狀態
     * 不屬於 COMPLETED/FAILED/CANCELLED/SKIPPED 時才會更新成功。
     */
    boolean updateContainerMainIdIfUnchanged(Long taskId,
                                             Long expectedOldContainerId,
                                             Long newContainerId);

    /**
     * 檢查指定 Gripper 是否存在「未完成」且指定目標與任務類型的任務
     * 未完成：排除 COMPLETED/FAILED/CANCELLED/SKIPPED，或（COMPLETED/FAILED 且 done_time IS NULL）
     *
     * @param gripperId        Gripper 裝置 ID
     * @param targetLocationId 目標點位 ID（to_location_id）
     * @param taskType         任務類型（如：DROP、PICK）
     * @return 是否存在
     */
    boolean existsUnfinishedTaskForGripperToTargetAndType(Long gripperId, Long targetLocationId, String taskType);

}
