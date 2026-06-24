package com.czkuo.rdf88701.config.plc;

import lombok.Data;

/**
 * 通用設備記憶體區段定義
 * - type: 記憶體區類型（B / W）
 * - address: 起始地址（十進位）
 * - length: 區段長度（bit 或 word）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class DeviceArea implements PlcArea {
    private String name;     // 區段名稱（可選）
    private String type;     // "B" 或 "W"
    private int address;     // 起始位址
    private int length;      // 長度（元件數）

    @Override
    public String getType() {
        return type;
    }

    @Override
    public int getAddress() {
        return address;
    }

    @Override
    public int getLength() {
        return length;
    }
}
