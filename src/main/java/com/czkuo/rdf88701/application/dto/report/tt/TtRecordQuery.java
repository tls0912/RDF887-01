package com.czkuo.rdf88701.application.dto.report.tt;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TtRecordQuery {

    private String deviceType;
    private String deviceName;

    private LocalDateTime from;
    private LocalDateTime to;

    private Integer transferNo;

    private long page = 1;
    private long size = 50;
}
