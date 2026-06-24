package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * R031 - 搬至 Manual Port 回覆 Payload
 * 廠商 → ASE：回報任務接收、執行、完成、失敗或取消
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class R031AckPayload {

    /** 指令分類，固定為 ROBOT */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代號，固定為 R031 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 傳輸識別碼（yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 STK_MOVE_SCH_TO_MANUAL_PORT */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 任務訊息內容 */
    @JsonProperty("MESSAGE")
    private Message message;

    /**
     * 任務狀態：
     * - OK：接收到任務
     * - START：開始執行
     * - END：完成搬運（RESULT_MESSAGE 為放置位置）
     * - FAIL：失敗（RESULT_MESSAGE 為原因）
     * - CANCEL：任務取消
     */
    @JsonProperty("RESULT")
    private String result;

    /** 結果說明（根據狀態給予位置或失敗原因） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 批號 */
        @JsonProperty("LOT_ID")
        private String lotId;

        /** 搬運容器編號 */
        @JsonProperty("CARRIERID")
        private String carrierId;

        /** 儲格名稱 */
        @JsonProperty("WIPNAME")
        private String wipName;
    }
}
