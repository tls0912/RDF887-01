package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.LocationReservationRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LocationReservationRecordRepository {

    /**
     * 依據 ID 查詢預約紀錄
     */
    Optional<LocationReservationRecord> findById(Long id);

    /**
     * 新增預約紀錄（自動寫入 history）
     */
    boolean save(LocationReservationRecord entity);

    /**
     * 更新預約紀錄（自動寫入 history）
     */
    boolean update(LocationReservationRecord entity);

    /**
     * 刪除預約紀錄（自動寫入 history）
     */
    boolean deleteById(Long id);

    /**
     * 查詢所有預約紀錄
     */
    List<LocationReservationRecord> findAll();

    /**
     * 查詢所有已過期但尚未標記的預約
     *
     * @param now 當前時間
     * @return 過期未標記的預約列表
     */
    List<LocationReservationRecord> findUnmarkedExpired(LocalDateTime now);

    /**
     * 查詢指定儲位上有效的預約（尚未 fulfilled / cancelled / expired 且未過期）
     *
     * @param locationPointId 儲位 ID
     * @return 有效預約 Optional
     */
    Optional<LocationReservationRecord> findActiveByLocationPoint(Long locationPointId);

    /**
     * 將指定預約標記為 fulfilled，並更新歷史表
     *
     * @param reservationId 預約 ID
     * @param fulfilledTime 完成時間
     * @return 是否更新成功
     */
    boolean markFulfilled(Long reservationId, LocalDateTime fulfilledTime);

    /**
     * 標記指定預約為已取消
     *
     * @param id 預約 ID
     * @param cancelledTime 取消時間
     * @param reason 原因
     * @return 是否更新成功
     */
    boolean markCancelled(Long id, LocalDateTime cancelledTime, String reason);

    /**
     * 標記指定預約為已過期
     *
     * @param id 預約 ID
     * @return 是否更新成功
     */
    boolean markExpired(Long id);

    /**
     * 延長（或設永不過期）指定預約的有效期。
     * - 若 newExpiredTime == null：表示「永不過期」
     * - 僅允許對「有效預約（未 fulfilled / cancelled / expired）」動手
     * - 可搭配服務層先以 findActiveByLocationPoint(...) 取得活躍預約再續命
     *
     * @param reservationId  預約ID
     * @param newExpiredTime 新的到期時間；null 表示永不過期
     * @return 是否更新成功；若找不到/非有效預約則回 false
     */
    boolean updateExpiredTime(Long reservationId, LocalDateTime newExpiredTime);

}
