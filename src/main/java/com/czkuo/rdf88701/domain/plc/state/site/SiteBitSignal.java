package com.czkuo.rdf88701.domain.plc.state.site;

/**
 * Site PLC Bit Signal 定義（PC→PLC Write）
 * - 以「Write-B 起始位址 + offset」定位：
 *   例：Site#2 base = B024C → offset 0=B024C, 1=B024D, 2=B024E, 3=B024F
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public enum SiteBitSignal {

    SITE_READY(0, "Site Ready (Enable/Disable)"), // base + 0
    SPARE_1(1, "Spare"),                          // base + 1
    REMOVE_ACCOUNT_ACK(2, "Remove Account Ack"),  // base + 2
    PORT_REPORT_ACK(3, "Port Report Ack");        // base + 3（若站無此位可忽略）

    private final int bitIndex;
    private final String description;

    SiteBitSignal(int bitIndex, String description) {
        this.bitIndex = bitIndex;
        this.description = description;
    }

    public int getBitIndex() {
        return bitIndex;
    }

    public String getDescription() {
        return description;
    }
}
