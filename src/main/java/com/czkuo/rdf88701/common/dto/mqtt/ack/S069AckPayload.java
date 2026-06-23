package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S069 指令的回覆（ACK）
 * 設備端回應是否成功處理告警訊息。
 */
@Data
public class S069AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S069" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤碼（與請求端一致） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "WARNING" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 逐筆處理結果明細 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 處理結果（OK / NG） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明，可為空字串或詳細錯誤內容 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 該筆對應的設備名稱（WIP / 拆併 / ZIPA / ZIPB / *） */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 該筆對應的告警代碼 */
        @JsonProperty("ALID")
        private String alid;

        /** 告警描述（英文） */
        @JsonProperty("ALID_DESC_EN")
        private String alidDescEn;

        /** 告警描述（中文） */
        @JsonProperty("ALID_DESC_CH")
        private String alidDescCh;
    }
}
