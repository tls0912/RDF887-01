package com.czkuo.rdf88701.presentation.web.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
/**
 * 更新容器 API request。
 *
 * <p>欄位為局部更新語意；null 表示不異動，data 不為 null 時會 upsert 最新內容資料。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public record UpdateContainerRequest(
        String containerType,

        @Size(max = 50)
        String containerCode,

        @Size(max = 50)
        String lotNo,

        @Size(max = 50)
        String partNo,

        ContainerDataRequest  data,

        @Positive
        Double trayThicknessMm
) {}
