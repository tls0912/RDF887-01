package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S010 回覆格式：回應刷卡驗證結果
 * 用於 ASE 回覆刷卡工號是否驗證通過。
 * 結果：
 * - OK：驗證成功，允許操作
 * - NG：驗證失敗，不允許操作
 */
@Data
public class S010AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S010" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS，需與請求方一致 */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述（固定為 "CARD_NUMBER_CHECK"） */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 回傳資料內容（包含刷卡人員的工號） */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 驗證結果：OK（通過） / NG（不通過） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果描述文字（可為空，或補充錯誤訊息） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：回覆的工號資訊
     */
    @Data
    public static class Message {

        /** 刷卡人員工號（與請求方一致） */
        @JsonProperty("CARD_NUMBER")
        private String cardNumber;

        /** 各機構區域 */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 各機構區安全門名稱 */
        @JsonProperty("SAFE_DOOR_NAME")
        private String safeDoorName;
    }
}
