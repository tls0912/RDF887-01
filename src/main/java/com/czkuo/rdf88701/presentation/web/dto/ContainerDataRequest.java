package com.czkuo.rdf88701.presentation.web.dto;

import jakarta.validation.constraints.Min;
/**
 * 容器內容資料 API request。
 *
 * <p>承載容器內容數量、層數、OCR 文字與內容種類，供建立或更新容器時一併寫入
 * container_data。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public record ContainerDataRequest(
        @Min(0) Integer verifiedQuantity,
        @Min(0) Integer estimatedQuantity,
        @Min(0) Integer coverLayers,
        @Min(0) Integer productLayers,
        String  ocrText1,
        String  ocrText2,
        String  contentKind

) {}
