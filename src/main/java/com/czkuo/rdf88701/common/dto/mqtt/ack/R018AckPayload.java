package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * R018 回覆格式（Ack）：回覆刪除任務執行情況
 * - 用於 SAA→ASE、SEEC→SAA 兩種場景
 * - MESSAGE 結構與指令相同
 */
@Data
public class R018AckPayload {

    /** 指令主類型，固定為 "ROBOT" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "R018" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /**
     * 任務識別碼，與原始刪除動作相同
     * 必須為本次刪除的 TID
     */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "DELETE_COMMAND" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /**
     * 任務細節，echo 原始請求
     * 只需帶被刪除任務的 TID
     */
    @JsonProperty("MESSAGE")
    private Message message;

    /**
     * 刪除結果
     * - OK：刪除成功
     * - FAIL：刪除失敗（需帶原因）
     */
    @JsonProperty("RESULT")
    private String result;

    /**
     * 結果說明
     * - OK 時可留空
     * - FAIL 時需填入失敗原因
     */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * R018 MESSAGE 物件
     * 欄位定義與指令相同
     */
    @Data
    public static class Message {

        /**
         * 被刪除的原始任務 TID
         * 格式：{CMD_ID}_{TID}，例："R007_20190220081111222"
         */
        @JsonProperty("CMD_TID")
        private String cmdTid;
    }
}
