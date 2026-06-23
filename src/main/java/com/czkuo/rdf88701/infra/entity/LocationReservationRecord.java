package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * <p>
 * 儲位預約主表
 * </p>
 *
 * @author czkuo
 * @since 2025-06-12
 */
@Getter
@Setter
@ToString
@TableName("location_reservation_record")
public class LocationReservationRecord {

    /**
     * 主鍵
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 預定放置的容器主 ID
     */
    private Long containerMainId;

    /**
     * 預定儲位位置 ID
     */
    private Long locationPointId;

    /**
     * 預約來源（如: AUTO_WALK、UI_MANUAL、SYSTEM_INTERNAL 等）
     */
    private String reservedBy;

    /**
     * 預約原因（選填）
     */
    private String reservedReason;

    /**
     * 預約建立時間
     */
    private LocalDateTime reservedTime;

    /**
     * 預期過期時間（NULL 表示永不過期）
     */
    private LocalDateTime expiredTime;

    /**
     * 是否已完成（0=尚未放置, 1=容器已放入）
     */
    private Boolean fulfilled;

    /**
     * 實際完成時間
     */
    private LocalDateTime fulfilledTime;

    /**
     * 是否已取消（0=否, 1=是）
     */
    private Boolean cancelled;

    /**
     * 取消時間
     */
    private LocalDateTime cancelledTime;

    /**
     * 取消原因
     */
    private String cancelledReason;

    /**
     * 是否已過期（系統排程標記用，不直接刪除）
     */
    private Boolean expired;
}
