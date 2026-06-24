package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S015 指令：零件預警設定（由 SAA 傳送至 SEEC）
 * 用於設定各項工具的使用上限與單位
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S015CommandPayload {

    /** 指令主類型，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S015 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務代碼，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 TOOL_REMIND_SETTING */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 工具預警設定內容 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 執行結果（傳送時可為空字串） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充結果說明文字（可為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 工具設定清單 */
        @JsonProperty("TOOL_LIST")
        private List<ToolSetting> toolList;
    }

    @Data
    public static class ToolSetting {

        /** 工具名稱，例如 AGV01手臂馬達 */
        @JsonProperty("TOOL_NAME")
        private String toolName;

        /** 預警設定上限值，例如 10000 */
        @JsonProperty("TOOL_LIMIT")
        private String toolLimit;

        /** 單位（如 KM、MIN） */
        @JsonProperty("UNIT")
        private String unit;
    }
}
