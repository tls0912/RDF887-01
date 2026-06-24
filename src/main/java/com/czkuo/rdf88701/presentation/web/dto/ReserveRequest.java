package com.czkuo.rdf88701.presentation.web.dto;

import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class ReserveRequest {
    /** 預約原因（可選） */
    private String reason;
}
