package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S001 指令：建立連線指令 Payload
 * 雙方皆可主動發送此格式（包含程式名稱與版本）
 */
@Data
public class S001CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S001" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS，具有延續性 */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述（固定為 "PC_LINK"） */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 指令內容：連線資訊 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 執行結果（初始為空字串） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明（初始為空字串，可為 null） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：建立連線的詳細資訊
     */
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
