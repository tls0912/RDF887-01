package com.czkuo.rdf88701.presentation.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
/**
 * 建立容器 API request。
 *
 * <p>建立 container_main 時使用，可同時帶入一筆 container_data 與托盤厚度 attr。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public record CreateContainerRequest(
        @NotBlank @Size(max = 20)
        String carrierId,

        @NotBlank
        String containerType,

        @Size(max = 50)
        String containerCode,

        @Size(max = 50)
        String lotNo,

        @Size(max = 50)
        String partNo,

        ContainerDataRequest data,

        @Positive
        Double trayThicknessMm
) {}
