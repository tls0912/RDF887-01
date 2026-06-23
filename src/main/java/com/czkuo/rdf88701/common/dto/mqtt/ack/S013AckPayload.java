package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S013 回覆格式：回應是否允許進行復歸或啟動操作
 * ASE 根據人員驗證結果回覆是否允許 RESET / START
 */
@Data
public class S013AckPayload {

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

    /** 回覆內容（包含人員工號清單） */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 執行結果（OK = 允許，NG = 拒絕） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明（可為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：人員清單
     */
    @Data
    public static class Message {

        /** 各機構區域 */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 參與復歸/啟動操作的人員工號清單 */
        @JsonProperty("STAFF_LIST")
        private List<String> staffList;
    }
}
