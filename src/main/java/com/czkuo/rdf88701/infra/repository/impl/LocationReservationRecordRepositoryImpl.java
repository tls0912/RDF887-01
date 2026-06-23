package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.LocationReservationRecordRepository;
import com.czkuo.rdf88701.infra.entity.LocationReservationHistory;
import com.czkuo.rdf88701.infra.entity.LocationReservationRecord;
import com.czkuo.rdf88701.infra.mapper.LocationReservationHistoryMapper;
import com.czkuo.rdf88701.infra.mapper.LocationReservationRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * LocationReservationRecordRepository 實作類別
 * <p>
 * 處理 location_reservation_record 表的 CRUD 操作，
 * 並在資料異動時自動寫入對應的 location_reservation_history 歷史表。
 */
@Repository
@RequiredArgsConstructor
public class LocationReservationRecordRepositoryImpl implements LocationReservationRecordRepository {

    private final LocationReservationRecordMapper locationReservationRecordMapper;
    private final LocationReservationHistoryMapper locationReservationHistoryMapper;

    /**
     * 依據主鍵查詢儲位預約紀錄
     *
     * @param id 主鍵 ID
     * @return 預約紀錄 Optional
     */
    @Override
    public Optional<LocationReservationRecord> findById(Long id) {
        return Optional.ofNullable(locationReservationRecordMapper.selectById(id));
    }

    /**
     * 儲存一筆新的儲位預約紀錄，並寫入歷史表
     *
     * @param entity 儲位預約紀錄
     * @return 是否儲存成功
     */
    @Override
    public boolean save(LocationReservationRecord entity) {
        boolean inserted = locationReservationRecordMapper.insert(entity) > 0;
        if (inserted) {
            archive(entity, "INSERT", "SYSTEM", null);
        }
        return inserted;
    }

    /**
     * 更新儲位預約紀錄，並寫入歷史表
     *
     * @param entity 預約紀錄
     * @return 是否更新成功
     */
    @Override
    public boolean update(LocationReservationRecord entity) {
        boolean updated = locationReservationRecordMapper.updateById(entity) > 0;
        if (updated) {
            archive(entity, "UPDATE", "SYSTEM", null);
        }
        return updated;
    }

    /**
     * 刪除指定的預約紀錄，並在刪除前寫入歷史表
     *
     * @param id 預約紀錄 ID
     * @return 是否刪除成功
     */
    @Override
    public boolean deleteById(Long id) {
        LocationReservationRecord record = locationReservationRecordMapper.selectById(id);
        if (record != null) {
            archive(record, "DELETE", "SYSTEM", null);
            return locationReservationRecordMapper.deleteById(id) > 0;
        }
        return false;
    }

    /**
     * 查詢全部儲位預約紀錄
     *
     * @return 所有預約紀錄清單
     */
    @Override
    public List<LocationReservationRecord> findAll() {
        return locationReservationRecordMapper.selectList(new QueryWrapper<>());
    }

    /**
     * 查詢所有已過期但尚未標記為 expired 的預約紀錄
     * 條件：
     * - expired_time 不為空，且小於當前時間
     * - 尚未 fulfilled
     * - 尚未 cancelled
     * - 尚未標記 expired
     *
     * @param now 當前時間（作為過期判定依據）
     * @return 未標記過期的預約清單
     */
    @Override
    public List<LocationReservationRecord> findUnmarkedExpired(LocalDateTime now) {
        return locationReservationRecordMapper.findUnmarkedExpired(now);
    }

    /**
     * 查詢指定儲位上尚未 fulfilled / cancelled / expired，
     * 且 expired_time 尚未過期的有效預約紀錄。
     *
     * @param locationPointId 儲位 ID
     * @return 有效預約紀錄（若無則為 empty）
     */
    @Override
    public Optional<LocationReservationRecord> findActiveByLocationPoint(Long locationPointId) {
        return Optional.ofNullable(locationReservationRecordMapper.selectActiveReservation(locationPointId));
    }

    /**
     * 將指定預約標記為 fulfilled，並寫入歷史表
     *
     * @param reservationId 預約主鍵 ID
     * @param fulfilledTime 完成時間
     */
    @Override
    public boolean markFulfilled(Long reservationId, LocalDateTime fulfilledTime) {
        LocationReservationRecord record = locationReservationRecordMapper.selectById(reservationId);
        if (record == null) {
            throw new IllegalArgumentException("Reservation not found: " + reservationId);
        }

        record.setFulfilled(true);
        record.setFulfilledTime(fulfilledTime);

        boolean updated = locationReservationRecordMapper.updateById(record) > 0;
        if (updated) {
            archive(record, "UPDATE", "SYSTEM", "Marked as fulfilled");
        }

        return updated;
    }

    /**
     * 將指定預約標記為已取消（cancelled），並寫入歷史表。
     *
     * @param reservationId 預約主鍵 ID
     * @param cancelledTime 取消時間
     * @param reason        取消原因（可為 null）
     */
    @Override
    public boolean markCancelled(Long reservationId, LocalDateTime cancelledTime, String reason) {
        LocationReservationRecord record = locationReservationRecordMapper.selectById(reservationId);
        if (record == null) {
            throw new IllegalArgumentException("Reservation not found: " + reservationId);
        }

        record.setCancelled(true);
        record.setCancelledTime(cancelledTime);
        record.setCancelledReason(reason);

        boolean updated = locationReservationRecordMapper.updateById(record) > 0;
        if (updated) {
            archive(record, "UPDATE", "SYSTEM", "Marked as cancelled: " + reason);
        }

        return updated;
    }

    /**
     * 將指定預約標記為已過期（expired），並寫入歷史表。
     *
     * @param reservationId 預約主鍵 ID
     */
    @Override
    public boolean markExpired(Long reservationId) {
        LocationReservationRecord record = locationReservationRecordMapper.selectById(reservationId);
        if (record == null) {
            throw new IllegalArgumentException("Reservation not found: " + reservationId);
        }

        record.setExpired(true);

        boolean updated = locationReservationRecordMapper.updateById(record) > 0;
        if (updated) {
            archive(record, "UPDATE", "SYSTEM", "Marked as expired");
        }

        return updated;
    }

    /**
     * 延長（或設為永不過期）指定預約的有效期。
     * 策略（簡單版）：
     * 1) 先以 selectById 取出紀錄
     * 2) 若已 fulfilled/cancelled/expired → 不允許續命，回 false
     * 3) 寫入 expiredTime，走 updateById（沿用你現有的「自動寫 history」機制）
     *
     * @param reservationId  預約ID
     * @param newExpiredTime 新的到期時間；null 表示永不過期
     * @return 是否更新成功；若找不到/非有效預約則回 false
     */
    @Override
    public boolean updateExpiredTime(Long reservationId, LocalDateTime newExpiredTime) {
        LocationReservationRecord record = locationReservationRecordMapper.selectById(reservationId);
        if (record == null) return false;

        // 僅對有效預約開放續命
        boolean ended = Boolean.TRUE.equals(record.getFulfilled())
                || Boolean.TRUE.equals(record.getCancelled())
                || Boolean.TRUE.equals(record.getExpired());
        if (ended) return false;

        // 永久 → 非永久：預設不允許縮短（若你要允許，移除此段）
        if (record.getExpiredTime() == null && newExpiredTime != null) {
            // 不變更，視為成功跳過
            return true;
        }

        // 只延長不縮短
        if (record.getExpiredTime() != null && newExpiredTime != null
                && !newExpiredTime.isAfter(record.getExpiredTime())) {
            return true; // 不動作
        }

        // 防呆：避免設成過去時間
        if (newExpiredTime != null && !newExpiredTime.isAfter(LocalDateTime.now())) {
            newExpiredTime = LocalDateTime.now().plusSeconds(1);
        }

        record.setExpiredTime(newExpiredTime);
        boolean updated = locationReservationRecordMapper.updateById(record) > 0;
        if (updated) {
            String remark = (newExpiredTime == null)
                    ? "Extend TTL → PERMANENT (expired_time=NULL)"
                    : "Extend TTL → " + newExpiredTime;
            archive(record, "UPDATE", "SYSTEM", remark);
        }
        return updated;
    }


    /**
     * 寫入 location_reservation_history 歷史紀錄
     * <p>
     * 注意：會排除 id 欄位，以避免與歷史表主鍵衝突。
     *
     * @param record     原始紀錄物件
     * @param changeType INSERT / UPDATE / DELETE
     * @param operator   操作者（通常為 SYSTEM 或登入者帳號）
     * @param remark     備註訊息，可為 null
     */
    private void archive(LocationReservationRecord record, String changeType, String operator, String remark) {
        LocationReservationHistory history = new LocationReservationHistory();
        BeanUtils.copyProperties(record, history, "id"); // 排除主鍵
        history.setOriginId(record.getId());
        history.setChangeType(changeType);
        history.setArchivedTime(LocalDateTime.now());
        history.setOperator(operator);
        history.setRemark(remark);
        locationReservationHistoryMapper.insert(history);
    }
}
