package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * S004 回覆格式（ACK）：提供目前 WIP 儲格的狀態
 * - 每筆儲格包含儲位名稱、啟用狀態、WIP 批號、盤數
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S004AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S004" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 回應對應的任務識別碼 */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述（固定為 "SYSTEM_COMPARE_DB"） */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 實際資料回傳區（包含 WIP 儲格清單） */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 回應結果（例如 OK、FAIL） */
    @JsonProperty("Result")
    private String result;

    /** 補充說明（可空） */
    @JsonProperty("Result_Message")
    private String resultMessage;

    /**
     * MESSAGE 區段：封裝多筆儲格資訊
     */
    @Data
    public static class Message {

        /** 儲格清單 */
        @JsonProperty("DATA")
        private List<WipData> data = new ArrayList<>(); ;

        /**
         * 工具方法：新增一筆儲格資訊
         *
         * @param wipName    儲格名稱（如 IN_WIP_001001）
         * @param status     儲格啟用狀態：ON / OFF
         * @param carrierId  盒號（可空）
         * @param lotId      批號（可空）
         * @param trayNum    Tray 數量
         */
        public void addWip(String wipName, String status, String carrierId, String lotId, String trayNum) {
            WipData slot = new WipData();
            slot.setWipName(wipName);
            slot.setStatus(status);
            slot.setCarrierId(carrierId);
            slot.setLotId(lotId);
            slot.setTrayNum(trayNum);
            this.data.add(slot);
        }
    }

    /**
     * 每一筆儲格對應資訊
     */
    @Data
    public static class WipData {

        /** 儲格名稱（如 IN_WIP_001001） */
        @JsonProperty("WIPNAME")
        private String wipName;

        /** 儲格啟用狀態：ON / OFF */
        @JsonProperty("STATUS")
        private String status;

        /** 盒號識別碼（如 TY0001VM） */
        @JsonProperty("CARRIERID")
        private String carrierId;

        /** 對應批號（可為空字串） */
        @JsonProperty("LOT_ID")
        private String lotId;

        /** 該儲格的 Tray 數量 */
        @JsonProperty("TRAY_NUM")
        private String trayNum;
    }
}
