package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * R031 - 通知從 WIP(STK) 搬貨至 Manual Port 指令 Payload
 * ASE → 廠商：下達搬運任務
 */
@Data
public class R031CommandPayload {

    /** 指令分類，固定為 ROBOT */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代號，固定為 R031 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 傳輸唯一識別碼（yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 STK_MOVE_SCH_TO_MANUAL_PORT */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 任務訊息內容 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 結果欄位（下達指令時為空） */
    @JsonProperty("RESULT")
    private String result;

    /** 錯誤或補充說明（失敗或取消時填寫） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 要搬運的批號（LOT） */
        @JsonProperty("LOT_ID")
        private String lotId;

        /** 盒號（容器 ID） */
        @JsonProperty("CARRIERID")
        private String carrierId;

        /** 搬運目標位置（Manual Port 儲格名稱） */
        @JsonProperty("WIPNAME")
        private String wipName;
    }
}
