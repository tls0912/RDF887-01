package com.czkuo.rdf88701.application.dto.report.Strapping;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StrappingStatsResult {
    private int successBundles;   // 成功幾把
    private int successShots;     // 成功 OK 次數（合計）
    private int failBundles;      // 失敗幾把（不足 required OK）
    private int failShots;        // 失敗 NG 次數 + 成功把內的 NG 次數（便於觀察 NG 負擔）
    private int abnormalBundles;  // 異常幾把（規則違反，例如 OK > required）
    private int abnormalShots;    // 異常次數（實際事件次數：空容器 or OK 超標的 shot 數）

    private double passRateValue;  // 通關率數值 (0.7692)
    private String passRate;       // 通關率百分比字串 ("76.92%")

    private List<StrappingSuccessRecord>  successDetails  = new ArrayList<>();
    private List<StrappingFailRecord>     failDetails     = new ArrayList<>();
    private List<StrappingAbnormalRecord> abnormalDetails = new ArrayList<>();
}
