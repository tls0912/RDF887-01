package com.czkuo.rdf88701.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OcrCommandResult
 * - 封裝 MCS→OCR 指令的回傳結果（HTTP/Webhook 無關）
 * - success：代表本次指令流程是否成功（例如完成送出/處理），不等同於 vendor 是否接受
 * - vendorAccepted：OCR 廠商是否接受（createTask 的 accepted）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrCommandResult {

    /** 本次指令流程是否成功（送出成功/邏輯處理完成） */
    private boolean success;

    /** 說明訊息（成功或失敗原因） */
    private String message;

    /** 本地生成的任務 ID（若有） */
    private Long taskId;

    /** 目標 OCR 裝置 ID（若有） */
    private Integer ocrDeviceId;

    /** 廠商是否接受（僅 createTask 等情境有意義） */
    private Boolean vendorAccepted;

    /** 廠商錯誤碼（DEVICE_BUSY / INVALID_DATA / INTERNAL_ERR / UNAUTHORIZED…） */
    private String vendorErrorCode;

    /** 結果產生時間 */
    private LocalDateTime timestamp;

    // ---------------- Factory Methods ----------------

    /** 成功（不帶任務/裝置信息） */
    public static OcrCommandResult success() {
        return new OcrCommandResult(true, "成功", null, null, null, null, LocalDateTime.now());
    }

    /** 成功（帶任務與裝置） */
    public static OcrCommandResult success(Long taskId, Integer ocrDeviceId) {
        return new OcrCommandResult(true, "成功", taskId, ocrDeviceId, null, null, LocalDateTime.now());
    }

    /** 成功（含 vendor 回覆摘要） */
    public static OcrCommandResult success(Long taskId, Integer ocrDeviceId,
                                           Boolean vendorAccepted, String vendorMessage, String vendorErrorCode) {
        return new OcrCommandResult(true,
                vendorMessage != null ? vendorMessage : "成功",
                taskId, ocrDeviceId, vendorAccepted, vendorErrorCode, LocalDateTime.now());
    }

    /** 失敗（簡單原因） */
    public static OcrCommandResult fail(String reason) {
        return new OcrCommandResult(false, reason, null, null, null, null, LocalDateTime.now());
    }

    /** 失敗（含任務/裝置/廠商錯誤碼） */
    public static OcrCommandResult fail(String reason, Long taskId, Integer ocrDeviceId, String vendorErrorCode) {
        return new OcrCommandResult(false, reason, taskId, ocrDeviceId, null, vendorErrorCode, LocalDateTime.now());
    }
}
