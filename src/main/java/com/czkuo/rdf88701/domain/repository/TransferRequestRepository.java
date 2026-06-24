package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.TransferRequest;
import java.util.List;
import java.util.Optional;

/**
 * TransferRequestRepository
 * <p>
 * Transfer 請求資料存取介面（Domain Layer）
 * - 封裝 TransferRequest 實體的資料庫操作
 * - 供應用服務層使用，避免直接耦合 Mapper
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface TransferRequestRepository {

    /**
     * 根據 ID 查詢 TransferRequest
     *
     * @param id TransferRequest 主鍵 ID
     * @return Optional 包裝的 TransferRequest 實體
     */
    Optional<TransferRequest> findById(Long id);

    /**
     * 新增 TransferRequest 資料
     *
     * @param entity 要新增的 TransferRequest 實體
     * @return 是否成功
     */
    boolean save(TransferRequest entity);

    /**
     * 更新 TransferRequest 資料
     *
     * @param entity 要更新的 TransferRequest 實體
     * @return 是否成功
     */
    boolean update(TransferRequest entity);

    /**
     * 根據 ID 刪除 TransferRequest
     *
     * @param id 要刪除的 TransferRequest 主鍵 ID
     * @return 是否成功
     */
    boolean deleteById(Long id);

    /**
     * 查詢所有 TransferRequest 資料
     *
     * @return TransferRequest 實體清單
     */
    List<TransferRequest> findAll();

    /**
     * 根據 request_key 查詢 TransferRequest
     *
     * @param requestKey 外部傳入的唯一識別碼
     * @return Optional 包裝的 TransferRequest 實體
     */
    Optional<TransferRequest> findByRequestKey(String requestKey);

    /**
     * 判斷指定 request_key 是否已存在
     *
     * @param requestKey 外部傳入的唯一識別碼
     * @return 是否存在
     */
    boolean existsByRequestKey(String requestKey);

    /**
     * 判斷指定 Transfer 裝置是否已有未完成請求（accepted = 'N'）
     *
     * @param deviceId Transfer 裝置 ID
     * @return 是否存在未完成請求
     */
    boolean existsUnfinishedRequestForDevice(Long deviceId);

    /**
     * 查詢所有尚未被接受的 TransferRequest（accepted = 'N'）
     *
     * @return TransferRequest 清單
     */
    List<TransferRequest> findUnacceptedRequests();

    /**
     * 查詢指定裝置尚未被接受的第一筆 TransferRequest（依建立時間排序）
     *
     * @param deviceId Transfer 裝置 ID
     * @return Optional 包裝的 TransferRequest 實體
     */
    Optional<TransferRequest> findFirstUnacceptedByDeviceId(String deviceId);
}
