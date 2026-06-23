package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S073 拆併前Tray資訊確認 指令
 * 廠商 → ASE：傳送 Tray 拍攝圖像與基本資訊，由 ASE 判斷是否允許進行拆併作業
 */
@Data
public class S073CommandPayload {

    /** 指令主類別，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S073 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務 ID，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /** 指令說明，固定為 TRAY_OCR_CHECK_INFO */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** Tray 資訊與圖像資料 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 處理結果（ASE 端填回） */
    @JsonProperty("RESULT")
    private String result;

    /** 錯誤或回應訊息內容 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {
        /** 批號 / 生產批次 */
        @JsonProperty("LOT_ID")
        private String lotId;

        /** Tray 類型代碼 */
        @JsonProperty("TRAY_TYPE")
        private String trayType;

        /** Tray 說明（含型號、尺寸等） */
        @JsonProperty("TRAY_DESC")
        private String trayDesc;

        /** 上蓋前一燈位置照片 */
        @JsonProperty("UPPER_COVER_TRAY_FRONT_ONE_LIGHT")
        private byte[] upperCoverTrayFrontOneLight;

        /** 上蓋前三燈位置照片 */
        @JsonProperty("UPPER_COVER_TRAY_FRONT_THREE_LIGHT")
        private byte[] upperCoverTrayFrontThreeLight;

        /** 上蓋後一燈位置照片 */
        @JsonProperty("UPPER_COVER_TRAY_BACK_ONE_LIGHT")
        private byte[] upperCoverTrayBackOneLight;

        /** 上蓋後三燈位置照片 */
        @JsonProperty("UPPER_COVER_TRAY_BACK_THREE_LIGHT")
        private byte[] upperCoverTrayBackThreeLight;

        /** Tray 本體前一燈位置照片 */
        @JsonProperty("TRAY_FRONT_ONE_LIGHT")
        private byte[] trayFrontOneLight;

        /** Tray 本體前三燈位置照片 */
        @JsonProperty("TRAY_FRONT_THREE_LIGHT")
        private byte[] trayFrontThreeLight;

        /** Tray 本體後一燈位置照片 */
        @JsonProperty("TRAY_BACK_ONE_LIGHT")
        private byte[] trayBackOneLight;

        /** Tray 本體後三燈位置照片 */
        @JsonProperty("TRAY_BACK_THREE_LIGHT")
        private byte[] trayBackThreeLight;

        // ===== 新增：8 張圖各自的 OCR 判讀答案（字串，無則空字串 ""） =====

        @JsonProperty("ANSWER_UPPER_COVER_TRAY_FRONT_ONE_LIGHT")
        private String answerUpperCoverTrayFrontOneLight;

        @JsonProperty("ANSWER_UPPER_COVER_TRAY_FRONT_THREE_LIGHT")
        private String answerUpperCoverTrayFrontThreeLight;

        @JsonProperty("ANSWER_UPPER_COVER_TRAY_BACK_ONE_LIGHT")
        private String answerUpperCoverTrayBackOneLight;

        @JsonProperty("ANSWER_UPPER_COVER_TRAY_BACK_THREE_LIGHT")
        private String answerUpperCoverTrayBackThreeLight;

        @JsonProperty("ANSWER_TRAY_FRONT_ONE_LIGHT")
        private String answerTrayFrontOneLight;

        @JsonProperty("ANSWER_TRAY_FRONT_THREE_LIGHT")
        private String answerTrayFrontThreeLight;

        @JsonProperty("ANSWER_TRAY_BACK_ONE_LIGHT")
        private String answerTrayBackOneLight;

        @JsonProperty("ANSWER_TRAY_BACK_THREE_LIGHT")
        private String answerTrayBackThreeLight;

        // ===== 新增：廠商彙總判斷結果與角度資訊 =====

        /** 此次 OCR 判斷結果（"PASS" 或 "FAIL"） */
        @JsonProperty("VENDER_RESULT")
        private String venderResult;

        /**
         * 此次 FAIL 位置（"F"=前耳, "B"=後耳, 兩者皆錯填 "F,B"；PASS 可留空）
         * 例："F,B"、"F"、"B"
         */
        @JsonProperty("VENDER_RESULT_FAIL")
        private String venderResultFail;

        /**
         * 上蓋區前/後耳文字角度（暫定耳朵靠左為前耳）
         * 格式："F0,B180"；0=正常，180=反向
         */
        @JsonProperty("VENDER_UPPER_COVER_TRAY_ANGLE")
        private String venderUpperCoverTrayAngle;

        /**
         * Tray 本體前/後耳文字角度（暫定耳朵靠左為前耳）
         * 格式："F0,B180"；0=正常，180=反向
         */
        @JsonProperty("VENDER_TRAY_ANGLE")
        private String venderTrayAngle;
    }
}
