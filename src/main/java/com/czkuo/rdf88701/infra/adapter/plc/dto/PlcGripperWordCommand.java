package com.czkuo.rdf88701.infra.adapter.plc.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Gripper → PLC 傳送指令資料封裝 DTO
 * - 對應 Word Memory W0260 ~ W027F（共 32 Word）
 * - 由 Application 組裝後交由 Encoder 編碼
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class PlcGripperWordCommand {

    /** 任務編號（Transfer No）- 對應 W0260 */
    private int transferNo;

    /**
     * 指令類型（BCD 編碼）
     * - 對應 W0261（低位為 TTTT）
     * - 1: MOVE, 2: PICK, 3: DROP
     */
    private int commandType;

    /** 托盤數量（Tray Quantity, BCD 編碼）- 對應 W0261（高位為 qqqq） */
    private int trayQuantity;

    /** Tray Height（DEC, 單位 0.01 mm，例如 5.62 mm → 傳送 562）- 對應 W0262 */
    private int trayHeight;

    /** Location Bank（DEC）- 對應 W0263 */
    private int locationBank;

    /** Location Bay（DEC）- 對應 W0264 */
    private int locationBay;

    /** Location Level（DEC）- 對應 W0265 */
    private int locationLevel;

    /**
     * 產品條碼（最多 50 字元，25 Word，每 Word 2 字元，ASCII 編碼）
     * - 對應 W0266 ~ W027E
     */
    private String productId;

    /** 備用欄位（W027F）- 保留用 */
    private Integer spareWord;
}
