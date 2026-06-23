package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.application.dto.query.InfraredTaskQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.infra.entity.InfraredTask;
import java.util.List;
import java.util.Optional;


/**
 * Infrared 任務資料存取介面
 * 作為 domain 層與 infra 的資料介接抽象
 */
public interface InfraredTaskRepository {

    /**
     * 查詢單筆（依 ID）
     */
    Optional<InfraredTask> findById(Long id);

    /**
     * 多條件查詢（不分頁）
     */
    List<InfraredTask> findByCondition(InfraredTaskQuery query);

    /**
     * 分頁查詢
     */
    PageResult<InfraredTask> findPageByCondition(InfraredTaskQuery query);

    /**
     * 查詢全部（不建議用於大量資料）
     */
    List<InfraredTask> findAll();

    /**
     * 新增任務
     */
    boolean save(InfraredTask entity);

    /**
     * 更新任務（以 ID 為主）
     */
    boolean update(InfraredTask entity);

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
    boolean existsUnfinishedTaskForInfrared(Long infraredId);

    /**
     * 查詢指定 Infrared 上優先處理的任務
     * 優先順序：DISPATCHED > PENDING，依 priority_level DESC
     */
    Optional<InfraredTask> findTopTaskByInfraredOrdered(int infraredId);
}
