package com.czkuo.rdf88701.application.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * LocationFlow 對外呈現用 VO
 */
@Data
public class LocationFlowVO {

    private Long id;

    private Long containerMainId;

    private Long locationPointId;

    private String entryType;

    private String exitType;

    private LocalDateTime arrivedTime;

    private LocalDateTime leftTime;

    private String entryOperator;

    private String exitOperator;

    private String remark;

    private LocalDateTime archivedTime;
}
