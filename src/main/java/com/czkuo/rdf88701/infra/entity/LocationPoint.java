package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author czkuo
 * @since 2025-05-06
 */
@Getter
@Setter
@ToString
@TableName("location_point")
public class LocationPoint {

    /**
     * 位置主鍵
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所屬邏輯區域（如 A/B 倉）
     */
    private String zoneCode;

    /**
     * 位置代碼
     */
    private String code;

    /**
     * 位置名稱（人性化顯示）
     */
    private String name;

    private BigDecimal coordinateX;

    private BigDecimal coordinateY;

    private BigDecimal coordinateZ;

    private Integer bank;

    private Integer bay;

    private Integer level;

    /**
     * 地點類型（如 STORAGE, SITE）
     */
    private String locationType;

    private String enabled;

    private String isOccupied;

    private String isLocked;

    private String isReserved;

    private String lockReason;

    /**
     * 偏好產品狀態
     */
    private String preferredStatus;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
