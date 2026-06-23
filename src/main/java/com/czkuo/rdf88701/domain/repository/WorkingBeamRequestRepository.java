package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;
import java.util.List;
import java.util.Optional;

/**
 * WorkingBeamRequest 資料存取介面
 * - 提供基本 CRUD 與業務邏輯用查詢方法
 */
public interface WorkingBeamRequestRepository {

    /**
     * 依 ID 查詢單筆
     */
    Optional<WorkingBeamRequest> findById(Long id);

    /**
     * 儲存新資料
     */
    boolean save(WorkingBeamRequest entity);

    /**
     * 更新既有資料
     */
    boolean update(WorkingBeamRequest entity);

    /**
     * 根據 ID 刪除資料
     */
    boolean deleteById(Long id);

    /**
     * 查詢所有資料
     */
    List<WorkingBeamRequest> findAll();

    /**
     * 根據 requestKey 查詢（建立時重複檢查或升級用）
     */
    Optional<WorkingBeamRequest> findByRequestKey(String requestKey);

    /**
     * 判斷 requestKey 是否已存在（建立前檢查）
     */
    boolean existsByRequestKey(String requestKey);

    /**
     * 判斷指定 WorkingBeam 是否有尚未接受的請求（例如：防止重複派工）
     */
    boolean existsUnfinishedRequestForBeam(Long workingBeamId);

    /**
     * 查詢所有尚未被接受（accepted = 'N'）的請求（Monitor 掃描用）
     */
    List<WorkingBeamRequest> findUnacceptedRequests();

    /**
     * 查詢指定 WorkingBeam 名稱的第一筆未被接受的 Request
     */
    Optional<WorkingBeamRequest> findFirstUnacceptedByWorkingBeamName(String workingBeamId);
}
