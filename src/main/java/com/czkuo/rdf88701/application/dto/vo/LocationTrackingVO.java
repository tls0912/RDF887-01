package com.czkuo.rdf88701.application.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * LocationTracking 對外呈現用 VO
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class LocationTrackingVO {

    private Long id;

    private Long containerMainId;

    private Long locationPointId;

    private String sourceType;

    private LocalDateTime arrivedTime;

    private LocalDateTime lastVerifiedTime;

    private LocalDateTime updatedTime;

    private Long flowId;
}
