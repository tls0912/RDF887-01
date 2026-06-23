package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.LocationReservationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 儲位預約主表 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-06-12
 */
@Mapper
public interface LocationReservationRecordMapper extends BaseMapper<LocationReservationRecord> {

    /**
     * 查詢指定儲位上，尚未 fulfilled / cancelled / expired，
     * 且過期時間（expired_time）為 NULL 或尚未到期的預約紀錄。
     * 若有多筆，只回傳最早建立的那一筆。
     *
     * @param locationPointId 儲位 ID
     * @return 有效預約紀錄（若無則回傳 null）
     */
    LocationReservationRecord selectActiveReservation(@Param("locationPointId") Long locationPointId);

    /**
     * 查詢所有已過期但尚未標記 expired 的預約紀錄
     * <p>
     * 條件包含：
     * - expired_time < now（已過期）
     * - fulfilled = false
     * - canceled = false
     * - expired = false
     *
     * @param now 當前時間
     * @return 未標記為過期的紀錄清單
     */
    List<LocationReservationRecord> findUnmarkedExpired(LocalDateTime now);
}
