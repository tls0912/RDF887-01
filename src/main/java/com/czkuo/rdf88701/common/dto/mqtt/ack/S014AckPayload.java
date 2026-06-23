package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S014 指令的回覆格式（Acknowledgement）
 * 用於回應零件預警清單結果（成功 / 失敗）
 */
@Data
public class S014AckPayload {

    /** 指令主類型，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S014 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 指令描述，固定為 TOOL_REMIND_LIST */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 任務代碼，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /** 工具清單內容 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 回覆結果，發送時初始為空，等待回覆時填寫（如 "OK"）*/
    @JsonProperty("RESULT")
    private String result;

    /** 回覆訊息，發送時初始為空，等待回覆時填寫 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 預警工具清單 */
        @JsonProperty("TOOL_LIST")
        private List<ToolInfo> toolList;
    }

    @Data
    public static class ToolInfo {

        /** 工具名稱，例如 AGV01手臂馬達 */
        @JsonProperty("TOOL_NAME")
        private String toolName;

        /** 目前使用量，例如 6000 */
        @JsonProperty("CURRENT_STATUS")
        private String currentStatus;

        /** 預警上限值，例如 10000 */
        @JsonProperty("TOOL_LIMIT")
        private String toolLimit;

        /** 單位（如 KM、MIN） */
        @JsonProperty("UNIT")
        private String unit;
    }
}
