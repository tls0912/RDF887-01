package com.czkuo.rdf88701.presentation.mqtt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MQTT 指令通用格式封裝（Java命名 + 保留 JSON 映射）
 * 對外 JSON 欄位仍維持 CMD, CMD_ID 等全大寫格式
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MqttCommand {

    /**
     * 指令類別，例如 SYSTEM / ROBOT / AGV
     */
    @JsonProperty("CMD")
    private String cmd;

    /**
     * 指令代碼，如 S001, S007, R007
     */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /**
     * 指令追蹤代碼（時間格式 yyyyMMddHHmmssSSS）
     */
    @JsonProperty("TID")
    private String tid;

    /**
     * 指令描述，如 PC_LINK, ALARM
     */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /**
     * 訊息主體（可能為 Map 或物件）
     */
    @JsonProperty("MESSAGE")
    private Object message;

    /**
     * 執行結果，如 OK / NG / ASSIGN / DONE
     */
    @JsonProperty("RESULT")
    private String result;

    /**
     * 結果補充說明（錯誤或提示）
     */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
