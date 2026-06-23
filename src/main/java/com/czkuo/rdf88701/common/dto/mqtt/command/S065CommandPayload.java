package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S065 指令：標籤資訊印製（格式一）
 * ASE → 廠商
 * - 用於請求印製標籤，內容包含批號與統計資訊
 */
@Data
public class S065CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S065" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤碼（yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "TAG_INFO" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 標籤列印資料清單（可擴充多筆） */
    @JsonProperty("MESSAGE")
    private List<TagInfo> message;

    /** 結果（預設為空） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果說明（預設為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * 標籤資訊資料結構
     */
    @Data
    public static class TagInfo {
        @JsonProperty("SCH")   // 批號
        private String sch;

        @JsonProperty("QTY")   // 整批數量
        private String qty;

        @JsonProperty("PASS")  // 通過數量
        private String pass;

        @JsonProperty("BGA")   // BGA 數量
        private String bga;

        @JsonProperty("BBI")   // BBI 數量
        private String bbi;

        @JsonProperty("MARK")  // MARK 數量
        private String mark;

        @JsonProperty("TPI")   // TPI 數量
        private String tpi;
    }
}
