package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * S020 指令：Event 發生通知
 * ------------------------------------------------------------
 * 設計要點：
 * 1) MESSAGE 內的欄位採「可選用」策略（@JsonInclude.NON_NULL），
 *    只會序列化實際需要的鍵，避免傳一堆 null。
 * 2) 對齊你提供的所有可能欄位鍵名（大小寫、底線、數字開頭等）：
 *    - LOT_ID、CARRIERID、STATUS、TYPE、WIPNAME、NUM、1D_BARCODE...
 * 3) 提供幾個靜態工廠方法，常用事件（2001/2002/2003、Port 狀態）直接呼叫就好。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class S020CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S020" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "EVENT" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 指令主體內容（事件資訊） */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 傳送時預設為空，或由回覆填入處理結果 */
    @JsonProperty("RESULT")
    private String result;

    /** 結果補充描述（例如 "Executing"、或將狀態/關鍵字串回填） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：事件內容定義（欄位為「可選用」）
     * - 注意：大小寫與鍵名完全對齊對方協議
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {

        /** 事件代碼（例如 "2003"） */
        @JsonProperty("CEID")
        private String ceid;

        /** 事件英文描述 */
        @JsonProperty("CEID_DESC_EN")
        private String ceidDescEn;

        /** 事件中文描述 */
        @JsonProperty("CEID_DESC_CH")
        private String ceidDescCh;

        /** Port/設備狀態（例如：Idle/Run/Alarm...） */
        @JsonProperty("STATUS")
        private String status;

        /** 批號（例如 LOT_ID："11YT11V001"） */
        @JsonProperty("LOT_ID")
        private String lotId;

        /** 載具（例如 CARRIERID："TY00021VM"） */
        @JsonProperty("CARRIERID")
        private String carrierId;

        /** 類型（例如 TYPE："STK"/"OUT"...） */
        @JsonProperty("TYPE")
        private String type;

        /** WIP 名稱（例如 "1025"） */
        @JsonProperty("WIPNAME")
        private String wipname;

        /** 數量（例如 "20"） */
        @JsonProperty("NUM")
        private String num;

        /** 一維條碼；鍵名以數字開頭，需用 @JsonProperty 顯式指定 */
        @JsonProperty("1D_BARCODE")
        private String oneDBarcode;

        /** 一般 BARCODE （3001/3002... 使用） */
        @JsonProperty("BARCODE")
        private String barcode;

        /** 料盤高度（字串表示即可） */
        @JsonProperty("TRAY_HIGH")
        private String trayHigh;

        /** 料盤類型 */
        @JsonProperty("TRAY_TYPE")
        private String trayType;

        /** 料盤數量 */
        @JsonProperty("TRAY_NUM")
        private String trayNum;

        /* ----------------------
         * 常用事件的工廠方法
         * ---------------------- */

        /** 2001：入庫完成 */
        public static Message of2001(String oneDBarcode, String lotId, String carrierId,
                                     String type, String wipname, String num) {
            return Message.builder()
                    .ceid("2001")
                    .ceidDescEn("")
                    .ceidDescCh("入庫完成")
                    .oneDBarcode(oneDBarcode)
                    .lotId(lotId)
                    .carrierId(carrierId)
                    .type(type)
                    .wipname(wipname)
                    .num(num)
                    .build();
        }

        /** 2002：出庫完成 */
        public static Message of2002(String oneDBarcode, String lotId, String carrierId,
                                     String type, String wipname, String num) {
            return Message.builder()
                    .ceid("2002")
                    .ceidDescEn("")
                    .ceidDescCh("出庫完成")
                    .oneDBarcode(oneDBarcode)
                    .lotId(lotId)
                    .carrierId(carrierId)
                    .type(type)
                    .wipname(wipname)
                    .num(num)
                    .build();
        }

        /** 2003：拆/併打帶完成，等待標籤資訊 */
        public static Message of2003(String lotId, String carrierId) {
            return Message.builder()
                    .ceid("2003")
                    .ceidDescEn("")
                    .ceidDescCh("拆/併打帶完成，等待標籤資訊")
                    .lotId(lotId)
                    .carrierId(carrierId)
                    .build();
        }

        /** 2004：產品放到貨架 */
        public static Message of2004(String lotId, String carrierId,
                                     String type, String wipname, String num) {
            return Message.builder()
                    .ceid("2004")
                    .ceidDescEn("")
                    .ceidDescCh("產品放到貨架")
                    .lotId(lotId)
                    .carrierId(carrierId)
                    .type(type)
                    .wipname(wipname)
                    .num(num)
                    .build();
        }

        /** 2005：產品離開貨架 */
        public static Message of2005(String lotId, String carrierId,
                                     String type, String wipname, String num) {
            return Message.builder()
                    .ceid("2005")
                    .ceidDescEn("")
                    .ceidDescCh("產品離開貨架")
                    .lotId(lotId)
                    .carrierId(carrierId)
                    .type(type)
                    .wipname(wipname)
                    .num(num)
                    .build();
        }

        /** 2008：產品離開貨架 */
        public static Message of2008(String oneDBarcode,String lotId, String carrierId,
                                     String type, String wipname, String num) {
            return Message.builder()
                    .ceid("2008")
                    .ceidDescEn("Product manually removed by operator")
                    .ceidDescCh("人員手動移除產品")
                    .oneDBarcode(oneDBarcode)
                    .lotId(lotId)
                    .carrierId(carrierId)
                    .type(type)
                    .wipname(wipname)
                    .num(num)
                    .build();
        }

        /** Port 類（1010/1011/1012/1014/1016/1017...）狀態變更 */
        public static Message ofPortStatus(String ceid, String ceidDescCh,
                                           String status, String lotId, String carrierId) {
            return Message.builder()
                    .ceid(ceid)
                    .ceidDescEn("")
                    .ceidDescCh(ceidDescCh)       // 例如 "ManualPort01狀態變更"
                    .status(status)               // 例如 "Idle"
                    .lotId(lotId)
                    .carrierId(carrierId)
                    .build();
        }

        /** STK/設備狀態變更（1004/1005/…），只帶中文描述與（可選）狀態值 */
        public static Message ofDeviceStatus(String ceid, String ceidDescCh, String status) {
            return Message.builder()
                    .ceid(ceid)
                    .ceidDescEn("")
                    .ceidDescCh(ceidDescCh) // 例如 "STK狀態變更"、"拆併機狀態變更"
                    .status(status)         // 例如 "Executing" / "Idle"
                    .build();
        }
    }

    /* ============================== */
    /* 3001~3009 ZIPA                 */
    /* ============================== */

    /** 3001：ZIPA 入料詢問 */
    public static Message of3001(String barcode) {
        return Message.builder()
                .ceid("3001")
                .ceidDescEn("ZIPA Inbound Request")
                .ceidDescCh("ZIPA 入料詢問")
                .barcode(barcode)
                .build();
    }

    /** 3002：ZIPA 入倉輸送中 */
    public static Message of3002(String barcode, String carrierId,
                                                    String lotId, String trayHigh, String trayType) {
        return Message.builder()
                .ceid("3002")
                .ceidDescEn("ZIPA Inbound Transfer")
                .ceidDescCh("ZIPA 入倉輸送中")
                .barcode(barcode)
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .build();
    }

    /** 3003：ZIPA 入倉搬運中(手臂) */
    public static Message of3003(String barcode, String carrierId,
                                                       String lotId, String trayHigh, String trayType) {
        return Message.builder()
                .ceid("3003")
                .ceidDescEn("ZIPA Inbound Handling (Arm)")
                .ceidDescCh("ZIPA 入倉搬運中(手臂)")
                .barcode(barcode)
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .build();
    }

    /** 3004：ZIPA 上架 */
    public static Message of3004(String barcode, String carrierId,
                                                    String lotId, String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3004")
                .ceidDescEn("ZIPA Storage Complete")
                .ceidDescCh("ZIPA 上架")
                .barcode(barcode)
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /** 3005：ZIPA 出倉搬運中(手臂) */
    public static Message of3005(String barcode, String carrierId,
                                                        String lotId, String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3005")
                .ceidDescEn("ZIPA Outbound Handling (Arm)")
                .ceidDescCh("ZIPA 出倉搬運中(手臂)")
                .barcode(barcode)
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /** 3006：ZIPA 出倉 */
    public static Message of3006(String barcode, String carrierId,
                                                     String lotId, String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3006")
                .ceidDescEn("ZIPA Outbound Complete")
                .ceidDescCh("ZIPA 出倉")
                .barcode(barcode)
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /** 3007：ZIPA 出庫輸送中 */
    public static Message of3007(String barcode, String carrierId,
                                                     String lotId, String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3007")
                .ceidDescEn("ZIPA Outbound Transfer")
                .ceidDescCh("ZIPA 出庫輸送中")
                .barcode(barcode)
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /** 3008：ZIPA 入庫失敗 */
    public static Message of3008(String barcode) {
        return Message.builder()
                .ceid("3008")
                .ceidDescEn("ZIPA Inbound Failed")
                .ceidDescCh("ZIPA 入庫失敗")
                .barcode(barcode)
                .build();
    }

    /** 3009：ZIPA 出庫失敗 */
    public static Message of3009(String barcode, String carrierId,
                                                   String lotId, String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3009")
                .ceidDescEn("ZIPA Outbound Failed")
                .ceidDescCh("ZIPA 出庫失敗")
                .barcode(barcode)
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /* ============================== */
    /* 3101~3107 WIP                  */
    /* ============================== */

    /** 3101：WIP 入倉輸送中 */
    public static Message of3101(String carrierId, String lotId,
                                                   String trayHigh, String trayType) {
        return Message.builder()
                .ceid("3101")
                .ceidDescEn("WIP Inbound Transfer")
                .ceidDescCh("WIP 入倉輸送中")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .build();
    }

    /** 3102：WIP 入倉搬運中(手臂) */
    public static Message of3102(String carrierId, String lotId,
                                                      String trayHigh, String trayType) {
        return Message.builder()
                .ceid("3102")
                .ceidDescEn("WIP Inbound Handling (Arm)")
                .ceidDescCh("WIP 入倉搬運中(手臂)")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .build();
    }

    /** 3103：WIP 上架 */
    public static Message of3103(String carrierId, String lotId,
                                                   String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3103")
                .ceidDescEn("WIP Storage Complete")
                .ceidDescCh("WIP 上架")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /** 3104：WIP 出倉搬運中(手臂) */
    public static Message of3104(String carrierId, String lotId,
                                                       String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3104")
                .ceidDescEn("WIP Outbound Handling (Arm)")
                .ceidDescCh("WIP 出倉搬運中(手臂)")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /** 3105：WIP 出倉 */
    public static Message of3105(String carrierId, String lotId,
                                                    String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3105")
                .ceidDescEn("WIP Outbound Complete")
                .ceidDescCh("WIP 出倉")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /** 3106：WIP 入庫失敗 */
    public static Message of3106(String carrierId, String lotId,
                                                 String trayHigh, String trayType) {
        return Message.builder()
                .ceid("3106")
                .ceidDescEn("WIP Inbound Failed")
                .ceidDescCh("WIP 入庫失敗")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .build();
    }

    /** 3107：WIP 出庫失敗 */
    public static Message of3107(String carrierId, String lotId,
                                                  String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3107")
                .ceidDescEn("WIP Outbound Failed")
                .ceidDescCh("WIP 出庫失敗")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /* ============================== */
    /* 3201~3203 拆併                  */
    /* ============================== */

    /** 3201：拆併 OCR 檢測 */
    public static Message of3201(String carrierId, String lotId,
                                                  String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3201")
                .ceidDescEn("Dismantle OCR Check")
                .ceidDescCh("拆併 OCR 檢測")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /** 3202：拆併 異物 檢測 */
    public static Message of3202(String carrierId, String lotId,
                                                            String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3202")
                .ceidDescEn("Dismantle Foreign Object Check")
                .ceidDescCh("拆併 異物 檢測")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /** 3203：拆併 打帶完成，等待標籤資訊 */
    public static Message of3203(String carrierId, String lotId,
                                                                   String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3203")
                .ceidDescEn("Dismantle Strapping Done Waiting Label")
                .ceidDescCh("拆併 打帶完成，等待標籤資訊")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /* ============================== */
    /* 3301~3305 ZIPB                 */
    /* ============================== */

    /** 3301：ZIPB 入料詢問 */
    public static Message of3301(String carrierId, String lotId,
                                                   String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3301")
                .ceidDescEn("ZIPB Inbound Request")
                .ceidDescCh("ZIPB 入料詢問")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /** 3302：ZIPB 入倉搬運中(手臂) */
    public static Message of3302(String carrierId, String lotId,
                                                       String trayHigh, String trayType) {
        return Message.builder()
                .ceid("3302")
                .ceidDescEn("ZIPB Inbound Handling (Arm)")
                .ceidDescCh("ZIPB 入倉搬運中(手臂)")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .build();
    }

    /** 3303：ZIPB 上架 */
    public static Message of3303(String carrierId, String lotId,
                                                    String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3303")
                .ceidDescEn("ZIPB Storage Complete")
                .ceidDescCh("ZIPB 上架")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /** 3304：ZIPB 出倉 */
    public static Message of3304(String carrierId, String lotId,
                                                     String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3304")
                .ceidDescEn("ZIPB Outbound Complete")
                .ceidDescCh("ZIPB 出倉")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /** 3305：ZIPB 入庫失敗 */
    public static Message of3305(String carrierId, String lotId,
                                                  String trayHigh, String trayType, String trayNum) {
        return Message.builder()
                .ceid("3305")
                .ceidDescEn("ZIPB Inbound Failed")
                .ceidDescCh("ZIPB 入庫失敗")
                .carrierId(carrierId)
                .lotId(lotId)
                .trayHigh(trayHigh)
                .trayType(trayType)
                .trayNum(trayNum)
                .build();
    }

    /* ----------------------
     * 指令層級工廠方法（方便組完整 payload）
     * ---------------------- */

    /** 建立一個基本的 S020 空殼，由呼叫端再 setMessage(...) */
    public static S020CommandPayload empty(String tid) {
        S020CommandPayload p = new S020CommandPayload();
        p.setCmd("SYSTEM");
        p.setCmdId("S020");
        p.setIdDesc("EVENT");
        p.setTid(tid);
        p.setResult("");
        p.setResultMessage("");
        return p;
    }

    /** 直接帶入 message 的便捷工廠 */
    public static S020CommandPayload of(String tid, Message message) {
        S020CommandPayload p = empty(tid);
        p.setMessage(message);
        return p;
    }
}
