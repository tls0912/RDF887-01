package com.czkuo.rdf88701.application.dto.command;

import lombok.Data;

/**
 * 外部系統呼叫建立 Crane Request DTO（傳入 location name 版）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class ExternalCraneRequestCreateCommand {

    private String requestKey;
    private String requestType;             // "INBOUND", "OUTBOUND", "RELOCATE"
    private String requestSource;           // "ASE", "SYSTEM", "UI"
    private String sourceRequestRef;

    private String containerMainCode;       // 外部 container code
    private String sourceLocationName;      // 外部傳入的 source location name
    private String targetLocationName;      // 外部傳入的 target location name

    private String operator;
    private String remark;
    private String rawPayload;
}
