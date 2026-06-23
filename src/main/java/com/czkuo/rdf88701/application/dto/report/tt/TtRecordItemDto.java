package com.czkuo.rdf88701.application.dto.report.tt;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TtRecordItemDto {

    private Integer stepNo;
    private String stepName;
    private Integer rawValue;
    private BigDecimal timeSec;
}
