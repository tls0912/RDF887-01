package com.czkuo.rdf88701.application.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * LocationFlow 對外呈現用 VO
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
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
