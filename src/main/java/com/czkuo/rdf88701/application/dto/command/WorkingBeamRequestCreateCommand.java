package com.czkuo.rdf88701.application.dto.command;

import lombok.Data;

/**
 * 外部新增 WorkingBeamRequest 使用的 Command DTO
 */
@Data
public class WorkingBeamRequestCreateCommand {

    private String requestKey;               // 外部識別唯一鍵
    private String requestSource;            // UI / SYSTEM
    private Long workingBeamId;              // 指定 WorkingBeam 裝置 ID
    private String direction;                // IN / OUT

    private String operator;                 // 操作者帳號（選填）
    private String remark;                   // 備註（選填）
    private String rawPayload;               // 原始 JSON 字串（選填）
}
