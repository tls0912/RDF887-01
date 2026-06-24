package com.czkuo.rdf88701.application.dto.report.Strapping;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class StrappingLogRow {
    private String productId;          // 產品 ID
    private int machinePos;            // 機台號 1/2/3
    private String result;             // "OK" or "NG"
    private LocalDateTime eventTime;   // 時間
}
