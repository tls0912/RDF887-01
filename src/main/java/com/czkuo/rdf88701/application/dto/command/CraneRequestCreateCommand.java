package com.czkuo.rdf88701.application.dto.command;

import lombok.Data;

/**
 * 外部新增 CraneRequest 使用的 Command DTO
 */
@Data
public class CraneRequestCreateCommand {

    private String requestKey;
    private String requestType;           // INBOUND / OUTBOUND / RELOCATE
    private String requestSource;         // UI / ASE / SYSTEM
    private String sourceRequestRef;      // 外部參考編號（選填）
    private Long containerMainId;
    private Long sourceLocationId;
    private Long targetLocationId;
    private String sourceLocationName;
    private String targetLocationName;
    private String operator;
    private String remark;
    private String rawPayload;            // 原始 JSON 字串（選填）
}
