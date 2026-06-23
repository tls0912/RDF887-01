package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S013 指令：人員觸發復歸/啟動請求
 * 廠商傳送欲執行 RESET / START 的人員工號，ASE 需進行資格驗證
 */
@Data
public class S013CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S013" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "RESET_CHECK" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 指令主體內容 */
    @JsonProperty("MESSAGE")
    private S013CommandPayload.Message message;

    /** 請求結果欄位（傳送時可預設為空，回應時填入 OK / NG） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果說明欄位（可為空字串或補充說明） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：維修門資訊
     */
    @Data
    public static class Message {

        /** 各機構區域 */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;
    }
}
