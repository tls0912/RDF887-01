package com.czkuo.rdf88701.application.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * LocationTracking 對外呈現用 VO
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
