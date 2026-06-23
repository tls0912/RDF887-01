package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * R018 指令：刪除任務指令
 * - ASE→SAA、SAA→SEEC 皆適用
 * - 用於通知對方刪除指定任務
 */
@Data
public class R018CommandPayload {

    /** 指令主類型，固定為 "ROBOT" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "R018" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /**
     * 任務識別碼（本次刪除動作的唯一識別）
     * 格式 yyyyMMddHHmmssSSS
     * 有延續性，請填入原始派工的 TID
     */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "DELETE_COMMAND" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /**
     * 任務細節，封裝要刪除的任務 TID
     */
    @JsonProperty("MESSAGE")
    private Message message;

    /**
     * 執行結果欄位
     * - 發送時必為空字串
     * - Ack 回覆時填入 "OK"（刪除成功）、"FAIL"（刪除失敗）
     */
    @JsonProperty("RESULT")
    private String result;

    /**
     * 結果說明
     * - 發送時為空字串
     * - 刪除失敗時需帶入原因說明
     */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * R018 MESSAGE 欄位說明
     * - 只需帶原始要刪除任務的 TID（格式如 "R007_20190220081111222"）
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
