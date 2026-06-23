package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S069 指令：手動觸發 WARNING（由 ASE 傳送至設備）
 * 類似 SECS S10FX 的 USER 定義告警，用於提示異常狀況。
 */
@Data
public class S069CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S069" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤碼（時間戳 yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "WARNING" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 訊息內容，包含告警代碼與中英文說明 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 處理結果（由設備端回傳） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明，可為空字串或詳細錯誤內容 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 內部類別：告警訊息內容
     */
    @Data
    public static class Message {

        /** 各機構區域 */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 告警代碼（Alarm ID） */
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
