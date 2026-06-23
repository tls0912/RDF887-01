package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 取任務圖片：Response */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrTaskImageResponse {
    @JsonProperty("taskId")      private Long taskId;
    @JsonProperty("ocrDeviceId") private Integer ocrDeviceId;
    @JsonProperty("imageBase64") private String imageBase64;
    // 若供應商支援原圖 URL，可再加欄位：private String imageUrl;
}
