package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.application.dto.query.CraneRequestQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.infra.entity.CraneRequest;

import java.util.List;
import java.util.Optional;

/**
 * Crane 請求資料存取介面
 */
public interface CraneRequestRepository {

    /**
     * 查詢單筆（依 ID）
     */
    Optional<CraneRequest> findById(Long id);

    /**
     * 依 requestKey 查詢（常用於判斷重複請求）
     */
    Optional<CraneRequest> findByRequestKey(String requestKey);

    /**
     * 是否已存在相同 requestKey 的請求（常用於建立前判斷）
     */
    boolean existsByRequestKey(String requestKey);

    /**
     * 是否已存在尚未完成的請求（根據 containerMainId）
     * 已接受（accepted='Y'）但尚未完成任務的請求
     */
    boolean existsUnfinishedRequestForContainer(Long containerMainId);

    /**
     * 判斷指定 Crane 裝置是否已有未完成請求（accepted = 'N'）
     *
     * @param deviceId Crane 裝置 ID
     * @return 是否存在未完成請求
     */
    boolean existsUnfinishedRequestForDevice(Long deviceId);

    /**
     * 多筆查詢（不分頁）
     */
    List<CraneRequest> findByCondition(CraneRequestQuery query);

    /**
     * 分頁查詢
     */
    PageResult<CraneRequest> findPageByCondition(CraneRequestQuery query);

    /**
     * 新增請求
     */
    boolean save(CraneRequest entity);

    /**
     * 更新請求（以 ID 為主）
     */
    boolean update(CraneRequest entity);

    /**
     * 刪除請求
     */
    boolean deleteById(Long id);

    /**
     * 查詢全部 Crane 請求（不建議大量資料使用）
     */
    List<CraneRequest> findAll();

    /**
     * 查詢所有尚未 accepted 的請求（accepted = 'N'）
     * 提供給 CraneRequestMonitor 監控掃描
     */
    List<CraneRequest> findUnacceptedRequests();

    /**
     * 指定來源位是否已存在未接受的 CraneRequest（等於 crane 將要去「取」那個位置）
     */
    boolean existsUnfinishedRequestPickFromLocation(Long deviceId, Long sourceLocationId);

    /**
     * 指定目標位是否已存在未接受的 CraneRequest（等於 crane 將要去「放」那個位置）
     */
    boolean existsUnfinishedRequestPlaceToLocation(Long deviceId, Long targetLocationId);

}
