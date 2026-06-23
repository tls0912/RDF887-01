package com.czkuo.rdf88701.application.dto.report.Alarm;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TriggerSpanRow {
    private int globalCode;
    private String itemType;
    private String equipment;
    private LocalDateTime triggerTime;
    private LocalDateTime clearTime;
    private long durationSeconds;
}
