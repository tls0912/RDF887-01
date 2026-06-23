package com.czkuo.rdf88701.domain.dto.zip.checktimer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

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
