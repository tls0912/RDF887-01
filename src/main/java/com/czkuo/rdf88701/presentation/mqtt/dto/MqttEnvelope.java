package com.czkuo.rdf88701.presentation.mqtt.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MQTT 指令通用外殼（型別安全版）
 * ------------------------------------------------------------
 * 1) 對外 JSON 鍵名維持協議大寫（CMD / CMD_ID / ...）
 * 2) MESSAGE 採用泛型 T，避免以 Object 承載造成轉型錯
 * 3) 預設忽略為 null 的欄位，避免傳一堆無用鍵
 * 4) 提供常用的靜態工廠方法（ofSystem / ok / fail）讓呼叫端更簡潔
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttEnvelope<T> {

    /** 指令大類（例：SYSTEM / ROBOT / AGV） */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼（例：S020, R007, A010） */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 追蹤代碼（例：yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述（例：EVENT / PC_LINK / ALARM ...） */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 指令主體（泛型：可為任一 DTO，例如 S020CommandPayload.Message） */
    @JsonProperty("MESSAGE")
    private T message;

    /** 執行結果（例：OK / NG / ASSIGN / DONE；一般由接收端填或留空） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果補充說明 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /* =========================================================
     * 便捷工廠方法（可視需求精簡/擴充）
     * ========================================================= */

    /** 最通用：自行指定 CMD/CMD_ID/ID_DESC */
    public static <T> MqttEnvelope<T> of(String cmd, String cmdId, String idDesc, String tid, T message) {
        return MqttEnvelope.<T>builder()
                .cmd(cmd)
                .cmdId(cmdId)
                .idDesc(idDesc)
                .tid(tid)
                .message(message)
                .result("")
                .resultMessage("")
                .build();
    }

    /** SYSTEM 類指令的便捷工廠（常見於 S00x / S02x） */
    public static <T> MqttEnvelope<T> ofSystem(String cmdId, String idDesc, String tid, T message) {
        return of("SYSTEM", cmdId, idDesc, tid, message);
    }

    /** ROBOT 類指令（Rxxx） */
    public static <T> MqttEnvelope<T> ofRobot(String cmdId, String idDesc, String tid, T message) {
        return of("ROBOT", cmdId, idDesc, tid, message);
    }

    /** AGV 類指令（Axxx） */
    public static <T> MqttEnvelope<T> ofAgv(String cmdId, String idDesc, String tid, T message) {
        return of("AGV", cmdId, idDesc, tid, message);
    }

    /** 設定結果為 OK（語意化鍊式呼叫） */
    public MqttEnvelope<T> ok() {
        this.result = "OK";
        if (this.resultMessage == null) this.resultMessage = "";
        return this;
    }

    /** 設定結果為 NG 並帶訊息（語意化鍊式呼叫） */
    public MqttEnvelope<T> fail(String message) {
        this.result = "NG";
        this.resultMessage = message;
        return this;
    }
}
