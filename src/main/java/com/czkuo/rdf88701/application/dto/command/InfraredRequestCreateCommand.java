package com.czkuo.rdf88701.application.dto.command;

import lombok.Data;

/**
 * 外部新增 InfraredRequest 使用的 Command DTO
 */
@Data
public class InfraredRequestCreateCommand {

    private String requestKey;       // 外部識別唯一鍵
    private String requestSource;    // UI / SYSTEM
    private Long infraredId;         // 指定 Infrared 裝置 ID
    private String taskType;         // 任務類型（如 MEASURE）

    private String operator;         // 操作者帳號（選填）
    private String remark;           // 備註（選填）
    private String rawPayload;       // 原始 JSON 字串（選填）
}
