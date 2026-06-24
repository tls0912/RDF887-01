package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** 取任務圖片（多張）：Response */
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrTaskImagesResponse {

    @JsonProperty("taskId")
    private Long taskId;

    @JsonProperty("ocrDeviceId")
    private Integer ocrDeviceId;

    @JsonProperty("images")
    private List<ImageItem> images;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageItem {
        @JsonProperty("index")
        private Integer index;

        @JsonProperty("imageBase64")
        private String imageBase64;

        // 若供應商同時支援原圖 URL，可擴充：private String imageUrl;
    }
}
