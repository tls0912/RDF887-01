package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.application.dto.query.WorkingBeamTaskQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;
import java.util.List;
import java.util.Optional;

/**
 * WorkingBeam 任務資料存取介面
 * 作為 domain 層與 infra 的資料介接抽象
 */
public interface WorkingBeamTaskRepository {

    /**
     * 查詢單筆（依 ID）
     */
    Optional<WorkingBeamTask> findById(Long id);

    /**
     * 多條件查詢（不分頁）
     */
    List<WorkingBeamTask> findByCondition(WorkingBeamTaskQuery query);

    /**
     * 分頁查詢
     */
    PageResult<WorkingBeamTask> findPageByCondition(WorkingBeamTaskQuery query);

    /**
     * 查詢全部（不建議用於大量資料）
     */
    List<WorkingBeamTask> findAll();

    /**
     * 新增任務
     */
    boolean save(WorkingBeamTask entity);

    /**
     * 更新任務（以 ID 為主）
     */
    boolean update(WorkingBeamTask entity);

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
     * 標記任務為結束（補上 done_time）
     */
    boolean markTaskAsDone(Long id);

    /**
     * 檢查是否存在尚未完成的任務（排除 COMPLETED / FAILED / CANCELLED / SKIPPED）
     */
    boolean existsUnfinishedTaskForBeam(Long workingBeamId);

    /**
     * 查詢指定 WorkingBeam 上優先處理的任務
     * 優先順序：DISPATCHED > PENDING，依 priority_level DESC
     */
    Optional<WorkingBeamTask> findTopTaskByWorkingBeamOrdered(int workingBeamId);
}
