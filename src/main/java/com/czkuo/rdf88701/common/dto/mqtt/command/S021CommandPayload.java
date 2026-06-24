package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S021 指令：詢問系統處理狀態（Process State）
 * ASE 主動向廠商查詢設備運作狀態（可接受 null message）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S021CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S021" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "SYSTEM_PROCESS_STATUS" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 請求端通常為 null；回覆時由對方填入實際狀態資訊 */
    @JsonProperty("MESSAGE")
    private List<Message> message;

    /** 執行結果（初始為空） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果補充描述（可為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：系統設備狀態
     */
    @Data
    public static class Message {

        /** 設備名稱（如 "STK", "ROBOT"） */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;
    }
}
