package com.czkuo.rdf88701.application.dto.report.Strapping;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StrappingAbnormalRecord {
    private String productId;
    private int machinePos;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int expectedOk;    // 理論 OK 次數
    private int actualOk;      // 實際 OK 次數（> expectedOk 等異常型）
    private int ngCount;       // 該把中的 NG 次數
    private String reason;     // 例如 "OK 次數超過理論值"
}
