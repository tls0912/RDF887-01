package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S066 指令：標籤資訊印製（格式二）
 * ASE → 廠商，用於傳送更詳細的標籤內容以供列印
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S066CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S066" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼（時間戳 yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "TAG_INFO" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 標籤印製資料清單（支援多筆同時列印） */
    @JsonProperty("MESSAGE")
    private List<Message> message;

    /** 處理結果（預設由接收方填入） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充結果說明（可為空字串） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：單筆標籤資料
     */
    @Data
    public static class Message {

        /** 批號，例如：089WEEV017 */
        @JsonProperty("lotid")
        private String lotId;

        /** Bin 類型，例如：R2、R3 */
        @JsonProperty("bintype")
        private String binType;

        /** Bin 編號，例如：2、3 */
        @JsonProperty("bincode")
        private String binCode;

        /** 此 Bin 類別的數量 */
        @JsonProperty("binqty")
        private String binQty;

        /** 所有 Bin 的總數量 */
        @JsonProperty("bintotal")
        private String binTotal;

        /** Bin 備註，使用 | 分隔換行文字 */
        @JsonProperty("binremark")
        private String binRemark;

        /** Bin 等級分類，例如 Green、Yellow */
        @JsonProperty("binclass")
        private String binClass;

        /** 條碼文字，例如：G;089WEEV017;R2;9;15;F */
        @JsonProperty("BarCode")
        private String barCode;

        /** 測試項目內容，例如：DSG / PA18 / 100 */
        @JsonProperty("TT")
        private String testType;

        /** 包裝型態，例如：VFBGA 5X5X0.45 (X2L MAP) */
        @JsonProperty("PKG")
        private String packageType;

        /** 錫球合金成分資訊 */
        @JsonProperty("SNBall")
        private String snBall;

        /** 助焊劑名稱，例如：SP6900 */
        @JsonProperty("Flux")
        private String flux;

        /** 回焊參數，例如：808OSP */
        @JsonProperty("Reflow")
        private String reflow;

        /** 重工記錄，支援多行說明 */
        @JsonProperty("Rework")
        private String rework;

        /** Marking 內容，可能為空字串 */
        @JsonProperty("Marking")
        private String marking;

        /** 使用者與時間紀錄，例如：K02114/4B 20230804 092317 */
        @JsonProperty("UserTime")
        private String userTime;

        /** 規格代碼，例如：64-31-5100-0006/04/16 */
        @JsonProperty("Spec")
        private String spec;
    }
}
