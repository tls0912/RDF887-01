package com.czkuo.rdf88701.application.dto.report.Alarm;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AlarmQuery {
    private LocalDateTime from;
    private LocalDateTime to;
    private String type;                // "ALARM" / "WARNING" / "ALL"
    private List<String> equipments;    // ["WIP","ZIPA",...]
    private String bucket;              // "day" / "hour"
}
