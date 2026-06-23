package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S081 更新儲格儲位資訊 - 回覆 Payload
 * 廠商 → ASE：回覆儲格更新是否成功
 */
@Data
public class S081AckPayload {

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

    /** 更新內容資料（與請求一致） */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 回應結果：PASS / FAIL */
    @JsonProperty("RESULT")
    private String result;

    /** 若失敗，需填寫錯誤原因 */
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
