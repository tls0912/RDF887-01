package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
/** 建立任務：Inbound Command（對外）— 不包含 taskId / createdTime */
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class OcrCreateTaskCommand {
    @JsonProperty("ocrDeviceId") @NotNull
    private Integer ocrDeviceId;

    @JsonProperty("containerId")
    private Long containerId;

    // 可選擇加：priority、clientRequestId（冪等控制）等欄位
}
