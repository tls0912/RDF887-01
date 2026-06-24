package com.czkuo.rdf88701.config.plc;

/**
 * 通用 PLC 區段定義介面
 * - 提供區段類型（B/W）、起始地址與長度，供 Polling Router 通用處理
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface PlcArea {

    /**
     * 區段型別（B 或 W）
     */
    String getType();

    /**
     * 起始位址（以元件為單位，非 byte）
     */
    int getAddress();

    /**
     * 長度（以元件為單位，Bit/Word 數量）
     */
    int getLength();
}
