package com.czkuo.rdf88701.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MqttSendResult
 * - 封裝 MQTT 指令發送的回應結果。
 * - 用於 UI 顯示、API 回傳或第三方系統查詢結果。
 * <p>
 * 功能說明：
 * - 表示該次 MQTT 發送是否成功（success）
 * - 顯示說明訊息（message），如 "發送成功" 或錯誤原因
 * - 若發送成功，包含該次指令的唯一識別碼（TID）供查詢
 * - 包含發送結果的時間（timestamp）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MqttSendResult {

    /**
     * 是否成功發送 MQTT 指令
     * - true：已成功送出（不代表 ACK 回覆成功）
     * - false：連線異常、格式錯誤、拋出例外等發送失敗
     */
    private boolean success;

    /**
     * 說明訊息
     * - 成功時：通常為 "發送成功"
     * - 失敗時：錯誤原因（如「MQTT 發送失敗：XXX」）
     */
    private String message;

    /**
     * 指令的唯一識別碼 TID（格式：yyyyMMddHHmmssSSS）
     * - 僅在發送成功或構建指令階段成功時才有
     * - 可供後續查詢 ACK、記錄、追蹤
     */
    private String tid;

    /**
     * 發送結果產生的時間
     * - 通常為發送當下的時間（非 ACK 回覆時間）
     */
    private LocalDateTime timestamp;

    /**
     * 建立成功回傳結果（不含 TID）
     *
     * @return 成功結果物件
     */
    public static MqttSendResult success() {
        return new MqttSendResult(true, "發送成功", null, LocalDateTime.now());
    }

    /**
     * 建立成功回傳結果
     *
     * @param tid 指令 TID（由指令建構階段產出）
     * @return 成功結果物件
     */
    public static MqttSendResult success(String tid) {
        return new MqttSendResult(true, "發送成功", tid, LocalDateTime.now());
    }

    /**
     * 建立失敗回傳結果（不含 TID）
     *
     * @param reason 失敗原因描述
     * @return 失敗結果物件
     */
    public static MqttSendResult fail(String reason) {
        return new MqttSendResult(false, reason, null, LocalDateTime.now());
    }

    /**
     * 建立失敗回傳結果（含 TID）
     *
     * @param reason 失敗原因描述
     * @param tid    該次指令的 TID（若建構階段已產生）
     * @return 失敗結果物件
     */
    public static MqttSendResult fail(String reason, String tid) {
        return new MqttSendResult(false, reason, tid, LocalDateTime.now());
    }
}