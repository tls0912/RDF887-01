package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S001 指令的回覆格式（Acknowledgement）
 * 回應對方的連線建立結果，例如 OK、FAIL，同時帶回連線資訊
 */
@Data
public class S001AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S001" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 回應所對應的任務代碼 TID（需與收到的請求一致） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述（固定為 "PC_LINK"） */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 連線資訊（程式名稱、版本、提示資訊） */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 執行結果（例如 "OK", "FAIL"） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果說明（可為空，或補充失敗原因） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 程式名稱（例如 "ProgramName.exe"） */
        @JsonProperty("PROGRAM_NAME")
        private String programName;

        /** 程式版本號（例如 "1.324.121"） */
        @JsonProperty("VER")
        private String version;

        /** 額外提示文字，例如系統來源、環境資訊等 */
        @JsonProperty("Hint")
        private String hint;
    }
}
