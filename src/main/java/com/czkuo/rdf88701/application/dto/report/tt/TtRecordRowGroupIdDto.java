package com.czkuo.rdf88701.application.dto.report.tt;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class TtRecordRowGroupIdDto {
    public TtRecordRowGroupIdDto(String remarkId, Integer cnt, BigDecimal cycleSec,String deviceArea) {
        this.remarkId = remarkId;
        this.cnt = cnt;
        this.cycleSec = cycleSec;
        this.deviceArea = deviceArea;
    }

    private String remarkId = "";
    private Integer cnt = 0;
    private BigDecimal cycleSec = BigDecimal.valueOf(0);
    private String deviceArea="";
}
