package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.application.dto.query.CraneTaskQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.infra.entity.CraneTask;

import java.util.List;
import java.util.Optional;

/**
 * Crane 任務資料存取介面
 * 作為 domain 層與 infra 的資料介接抽象
 */
public interface CraneTaskRepository {

    /**
     * 查詢單筆（依 ID）
     */
    Optional<CraneTask> findById(Long id);

    /**
     * 查詢 craneId 下所有 PENDING 任務（for monitor dispatch）
     */
    List<CraneTask> findPendingTasksByCraneId(String craneId);

    /**
     * 查詢 craneId 下第一筆 PENDING 任務（用於 handshake 推進）
     */
    Optional<CraneTask> findNextPendingTask(String craneId);

    /**
     * 是否存在 craneId 下未完成任務（非 COMPLETED / CANCELLED）
     */
    boolean existsUnfinishedTaskForCrane(String craneId);

    /**
     * 多條件查詢（不分頁）
     */
    List<CraneTask> findByCondition(CraneTaskQuery query);

    /**
     * 分頁查詢
     */
    PageResult<CraneTask> findPageByCondition(CraneTaskQuery query);

    /**
     * 查詢全部（不建議用於大量資料）
     */
    List<CraneTask> findAll();

    /**
     * 查詢所有未完成任務（PENDING / DISPATCHED）
     * 用於 CraneTaskMonitor 任務監控排程
     */
    List<CraneTask> findPendingOrDispatchedTasks();

    /**
     * 查詢單一 crane 的最高優先任務（DISPATCHED > PENDING, priority DESC）
     */
    Optional<CraneTask> findTopTaskByCraneOrdered(int craneId);

    /**
     * 查詢指定容器的最新任務
     * - 依 created_time DESC 排序取第一筆
     */
    Optional<CraneTask> findLatestByContainerMainId(Long containerMainId);

    /**
     * 新增任務
     */
    boolean save(CraneTask entity);

    /**
     * 更新任務（以 ID 為主）
     */
    boolean update(CraneTask entity);

    /**
     * 刪除任務（依 ID）
     */
    boolean deleteById(Long id);

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
     * 標記指定結束時間
     */
    boolean markTaskAsDone(Long id);

    /**
     * 指定來源位是否存在未完成 CraneTask（crane 將要去「取」該來源位）
     */
    boolean existsUnfinishedTaskPickFromLocation(String craneId, Long sourceLocationId);

    /**
     * 指定目標位是否存在未完成 CraneTask（crane 將要去「放」到該目標位）
     */
    boolean existsUnfinishedTaskPlaceToLocation(String craneId, Long targetLocationId);
}
