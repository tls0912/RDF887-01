package com.czkuo.rdf88701.application.dto.command;

import lombok.Data;

/**
 * 外部新增 TransferRequest 使用的 Command DTO
 */
@Data
public class TransferRequestCreateCommand {

    private String requestKey;               // 外部識別唯一鍵
    private String requestSource;            // UI / SYSTEM
    private Long transferId;                 // 指定 Transfer 裝置 ID
    private String taskType;                 // INBOUND / OUTBOUND / RELOCATE

    private Long sourceLocationId;          // 來源位置 ID（可為 NULL）
    private Long targetLocationId;          // 目標位置 ID（可為 NULL）

    private String sourceLocationName;      // 顯示用名稱（選填）
    private String targetLocationName;      // 顯示用名稱（選填）

    private String operator;                // 操作者帳號（選填）
    private String remark;                  // 備註（選填）
    private String rawPayload;              // 原始 JSON 字串（選填）
}
