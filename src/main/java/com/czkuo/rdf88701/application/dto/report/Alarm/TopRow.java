package com.czkuo.rdf88701.application.dto.report.Alarm;

import lombok.Data;

@Data
public class TopRow {
    private int globalCode;
    private String titleZh;
    private String titleEn;
    private long count;          // or totalDurationSeconds
    private long totalDurationSeconds;
}
