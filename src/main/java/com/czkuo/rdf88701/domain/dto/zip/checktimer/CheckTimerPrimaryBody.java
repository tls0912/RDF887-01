package com.czkuo.rdf88701.domain.dto.zip.checktimer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class CheckTimerPrimaryBody {
    @JsonProperty("TimerInfo") private TimerInfo timerInfo;

    @Data
    public static class TimerInfo {
        @JsonProperty("Year")   private int year;
        @JsonProperty("Mon")    private int mon;
        @JsonProperty("Day")    private int day;
        @JsonProperty("Hour")   private int hour;
        @JsonProperty("Minute") private int minute;
        @JsonProperty("Second") private int second;
    }
}
