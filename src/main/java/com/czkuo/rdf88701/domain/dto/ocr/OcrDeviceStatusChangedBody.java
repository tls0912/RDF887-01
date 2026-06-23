package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 設備狀態變更通知 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrDeviceStatusChangedBody {

    @JsonProperty("ocrDeviceId")
    private Integer ocrDeviceId;

    @JsonProperty("status") // IDLE/BUSY/ERROR/MAINTENANCE/OFFLINE
    private String status;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
}
