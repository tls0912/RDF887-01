package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 取任務圖片：Response */
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrTaskImageResponse {
    @JsonProperty("taskId")      private Long taskId;
    @JsonProperty("ocrDeviceId") private Integer ocrDeviceId;
    @JsonProperty("imageBase64") private String imageBase64;
    // 若供應商支援原圖 URL，可再加欄位：private String imageUrl;
}
