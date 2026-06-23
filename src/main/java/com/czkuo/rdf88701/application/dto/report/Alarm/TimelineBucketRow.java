package com.czkuo.rdf88701.application.dto.report.Alarm;

import lombok.Data;

@Data
public class TimelineBucketRow {
    private String bucketStart;  // "2025-10-17 00:00:00"
    private long triggerCount;
    private long clearCount;
}
