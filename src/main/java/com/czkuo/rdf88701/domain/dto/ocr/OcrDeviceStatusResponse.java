package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 查設備狀態：Response */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrDeviceStatusResponse {
    @JsonProperty("ocrDeviceId")    private Integer ocrDeviceId;
    @JsonProperty("status")         private String status; // IDLE/BUSY/ERROR/MAINTENANCE/OFFLINE
    @JsonProperty("lastActiveTime") private LocalDateTime lastActiveTime;
    @JsonProperty("acceptingTask")  private Boolean acceptingTask; // 供應商若支援
}
