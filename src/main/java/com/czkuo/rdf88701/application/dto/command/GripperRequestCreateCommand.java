package com.czkuo.rdf88701.application.dto.command;

import lombok.Data;

/**
 * 外部新增 GripperRequest 使用的 Command DTO
 * - 用於觸發 Gripper 執行 PICK / MOVE / PLACE 任務
 */
@Data
public class GripperRequestCreateCommand {

    private String requestKey;             // 請求唯一識別碼（外部來源）
    private String requestSource;          // 請求來源（如 UI / SYSTEM）
    private Long gripperId;                // Gripper 裝置 ID
    private String taskType;               // 任務類型：PICK / MOVE / PLACE

    private Long sourceLocationId;          // 來源位置 ID（可為 NULL）
    private Long targetLocationId;          // 目標位置 ID（可為 NULL）

    private String sourceLocationName;     // 顯示用途來源位置名稱
    private String targetLocationName;     // 顯示用途目標位置名稱

    private String operator;               // 操作者帳號
    private String remark;                 // 備註說明
    private String rawPayload;             // 原始 JSON 字串（外部傳入完整上下文）
}
