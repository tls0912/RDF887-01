package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S081 更新儲格儲位資訊 - 指令 Payload
 * ASE → 廠商：更新某儲位的對應 LOT/CARRIER
 */
@Data
public class S081CommandPayload {

    /** 指令類型，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S081 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 訊息流水號（格式：yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令說明，固定為 WIP_INFO_UPDATE */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 更新內容資料 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 結果代碼，請求時為空字串 */
    @JsonProperty("RESULT")
    private String result;

    /** 結果訊息補充 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 儲位名稱，例如 IN_LEFT_1012 */
        @JsonProperty("WIPNAME")
        private String wipName;

        /** 搬運載具代碼，例如 11TY00V002_P_1 */
        @JsonProperty("CARRIERID")
        private String carrierId;

        /** LOT ID，例如 11TY00V002 */
        @JsonProperty("LOT_ID")
        private String lotId;
    }
}
