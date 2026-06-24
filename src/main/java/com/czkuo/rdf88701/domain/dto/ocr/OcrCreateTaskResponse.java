package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 建立任務：Response */
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrCreateTaskResponse {
    @JsonProperty("accepted")  private Boolean accepted;
    @JsonProperty("message")   private String message;
    @JsonProperty("errorCode") private String errorCode; // DEVICE_BUSY / INVALID_DATA / INTERNAL_ERR / UNAUTHORIZED
}
