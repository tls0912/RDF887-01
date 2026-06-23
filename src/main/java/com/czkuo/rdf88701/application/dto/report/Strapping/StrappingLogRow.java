package com.czkuo.rdf88701.application.dto.report.Strapping;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StrappingLogRow {
    private String productId;          // 產品 ID
    private int machinePos;            // 機台號 1/2/3
    private String result;             // "OK" or "NG"
    private LocalDateTime eventTime;   // 時間
}
