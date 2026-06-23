package com.czkuo.rdf88701.application.dto.report.tt;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TtQueryFilterDto {

    private LocalDateTime from;
    private LocalDateTime to;

    private String deviceType;
    private String deviceName;
    private String remarkId;

    private Integer transferNo;
}
