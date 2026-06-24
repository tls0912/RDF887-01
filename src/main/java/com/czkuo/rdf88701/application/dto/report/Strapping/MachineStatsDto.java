package com.czkuo.rdf88701.application.dto.report.Strapping;

import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class MachineStatsDto {
    private byte   machinePos;       // 1/2/3
    private String machineName;      // e.g. "STRAP#1"

    private int    successBundles;   // 成功幾把
    private int    successShots;     // 成功 OK 次數
    private int    failShots;        // 失敗(含成功把內NG) 次數
    private int    abnormalBundles;  // 異常幾把
    private int    abnormalShots;    // 異常次數 (OK 超標、空容器等)

    private double passRateValue;    // 0.75
    private String passRate;         // "75.00%"
}
