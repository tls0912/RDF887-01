package com.czkuo.rdf88701.application.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class LocationPointVO {

    private Long id;

    private String zoneCode;

    private String code;

    private String name;

    private BigDecimal coordinateX;

    private BigDecimal coordinateY;

    private BigDecimal coordinateZ;

    private Integer bank;

    private Integer bay;

    private Integer level;

    private String locationType;

    private String enabled;

    private String isOccupied;

    private String isLocked;

    private String isReserved;

    private String lockReason;

    private String preferredStatus;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
