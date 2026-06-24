package com.czkuo.rdf88701.presentation.web.dto;

import java.time.LocalDateTime;
/**
 * 容器主資料 API 回應模型。
 *
 * <p>聚合 container_main、最新 container_data 與托盤厚度 attr，供前端容器列表與
 * 明細畫面使用。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public record ContainerMainDto(
        Long id,
        String carrierId,
        String containerType,
        String containerCode,
        String lotNo,
        String partNo,
        LocalDateTime createdTime,
        Integer verifiedQuantity,
        Integer estimatedQuantity,
        Integer coverLayers,
        Integer productLayers,
        String ocrText1,
        String ocrText2,
        String contentKind,
        Double trayThicknessMm
) {}
