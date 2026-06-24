package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.GripperRequest;
import java.util.List;
import java.util.Optional;

/**
 * GripperRequestRepository
 * <p>
 * Gripper 請求資料存取介面（Domain Layer）
 * - 封裝 GripperRequest 實體的資料庫操作
 * - 提供應用服務層使用，避免直接耦合 Mapper
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface GripperRequestRepository {

    /**
     * 根據 ID 查詢 GripperRequest
     *
     * @param id 主鍵 ID
     * @return Optional 包裝的實體
     */
    Optional<GripperRequest> findById(Long id);

    /**
     * 新增 GripperRequest
     *
     * @param entity 實體資料
     * @return 是否成功
     */
    boolean save(GripperRequest entity);

    /**
     * 更新 GripperRequest
     *
     * @param entity 實體資料
     * @return 是否成功
     */
    boolean update(GripperRequest entity);

    /**
     * 根據 ID 刪除 GripperRequest
     *
     * @param id 主鍵 ID
     * @return 是否成功
     */
    boolean deleteById(Long id);

    /**
     * 查詢所有 GripperRequest
     *
     * @return 實體清單
     */
    List<GripperRequest> findAll();

    /**
     * 根據 request_key 查詢 GripperRequest
     *
     * @param requestKey 外部唯一識別碼
     * @return Optional 包裝的實體
     */
    Optional<GripperRequest> findByRequestKey(String requestKey);

    /**
     * 判斷指定 request_key 是否已存在
     *
     * @param requestKey 識別碼
     * @return 是否存在
     */
    boolean existsByRequestKey(String requestKey);

    /**
     * 判斷指定 Gripper 裝置是否存在未完成請求（accepted = 'N'）
     *
     * @param gripperId Gripper 裝置 ID
     * @return 是否存在未完成請求
     */
    boolean existsUnfinishedRequestForDevice(Long gripperId);

    /**
     * 查詢所有未被接受的 GripperRequest（accepted = 'N'）
     *
     * @return 未接受請求清單
     */
    List<GripperRequest> findUnacceptedRequests();

    /**
     * 查詢指定裝置尚未被接受的第一筆 GripperRequest（依建立時間排序）
     *
     * @param gripperId Gripper 裝置 ID
     * @return Optional 包裝的第一筆未接受請求
     */
    Optional<GripperRequest> findFirstUnacceptedByDeviceId(String gripperId);

    /**
     * 判斷指定 Gripper 是否存在「未完成(accepted='N')」且指定目標與任務類型的請求
     *
     * @param gripperId        Gripper 裝置 ID
     * @param targetLocationId 目標點位 ID
     * @param taskType         任務類型（如：DROP、PICK）
     * @return 是否存在
     */
    boolean existsUnfinishedRequestForDeviceToTargetAndType(Long gripperId, Long targetLocationId, String taskType);

}
