package com.czkuo.rdf88701.domain.plc.state.infrared;

/**
 * Infrared PLC Bit Signal 定義
 * - 專供 PC → PLC Bit 指令 (Infrared Indicate Bit Data)
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public enum InfraredBitSignal {

    // ==========================
    // Measure Job Handshake
    // ==========================
    INFRARED_READY(0, "Infrared Ready - 表示 PC 已準備下達測量指令"),
    SPARE_1(1, "Spare"),
    SPARE_2(2, "Spare"),
    MEASURE_CMD_REQ(3, "Measure Height CMD Req - 請求 PLC 執行測量指令"),
    MEASURE_COMP_ACK(4, "Measure Height Comp Ack - 回應 PLC 測量完成確認"),
    SPARE_5(5, "Spare"),
    SPARE_6(6, "Spare"),
    SPARE_7(7, "Spare");

    private final int bitIndex;
    private final String description;

    InfraredBitSignal(int bitIndex, String description) {
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
