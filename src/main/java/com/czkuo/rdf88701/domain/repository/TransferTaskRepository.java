package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.application.dto.query.TransferTaskQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.infra.entity.TransferTask;
import java.util.List;
import java.util.Optional;

/**
 * Transfer 任務資料存取介面
 * - 作為 domain 層與 infra 資料層的介接抽象
 * - 提供查詢、狀態變更與任務操作等方法
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface TransferTaskRepository {

    /**
     * 查詢單筆（依 ID）
     */
    Optional<TransferTask> findById(Long id);

    /**
     * 多條件查詢（不分頁）
     */
    List<TransferTask> findByCondition(TransferTaskQuery query);

    /**
     * 分頁查詢
     */
    PageResult<TransferTask> findPageByCondition(TransferTaskQuery query);

    /**
     * 查詢全部（不建議用於大量資料）
     */
    List<TransferTask> findAll();

    /**
     * 查詢指定容器的最新任務（依 created_time DESC 取第一筆）
     */
    Optional<TransferTask> findLatestByContainerMainId(Long containerMainId);

    /**
     * 取「指定容器 + 指定 Transfer」的最新一筆任務（不過濾狀態）
     * */
    Optional<TransferTask> findLatestByContainerAndTransfer(Long transferId, Long containerMainId);

    /**
     * 新增任務
     */
    boolean save(TransferTask entity);

    /**
     * 更新任務（以 ID 為主）
     */
    boolean update(TransferTask entity);

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
    boolean existsUnfinishedTaskForTransfer(Long transferId);

    /**
     * 查詢指定 Transfer 裝置上優先處理的任務
     * 優先順序：DISPATCHED > PENDING，依 priority_level DESC
     */
    Optional<TransferTask> findTopTaskByTransferOrdered(int transferId);

    /**
     * 根據容器 ID 查詢最後一筆有效 PICK 任務（目標是此 Transfer）
     */
    Optional<TransferTask> findLastPickTaskByContainer(Long transferId, Long containerMainId);
}
