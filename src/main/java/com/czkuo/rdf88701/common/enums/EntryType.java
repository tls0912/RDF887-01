package com.czkuo.rdf88701.common.enums;

/**
 * 帳務建立方式（對應 location_flow.entry_type）
 */
public enum EntryType {
    PLC,        // 由 PLC 建帳（通常為自動搬運設備回報）
    MANUAL,     // 手動建帳（人工輸入）
    EXTERNAL,   // 由外部系統建帳（例如 MES）
    REBUILD     // 系統自動重建帳（例如資料復原、同步）
}
