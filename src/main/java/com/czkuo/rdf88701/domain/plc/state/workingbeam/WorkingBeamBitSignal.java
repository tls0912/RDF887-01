package com.czkuo.rdf88701.domain.plc.state.workingbeam;

/**
 * WorkingBeam PLC Bit Signal 定義
 * - 專供 PC → PLC Bit 指令 (Working Beam Indicate Bit Data)
 */
public enum WorkingBeamBitSignal {

    // ==========================
    // Transfer Job Handshake
    // ==========================
    TRANSFER_READY(0, "Transfer Ready - 表示 PC 已準備下達指令"),
    SPARE_1(1, "Spare"),
    SPARE_2(2, "Spare"),

    // ==========================
    // Reserved / 擴充用位元
    // ==========================
    SPARE_3(3, "Spare"),
    SPARE_4(4, "Spare"),
    TRANSFER_CMD_REQ(5, "Transfer Cmd Req - 請求 PLC 執行搬運指令"),
    TRANSFER_COMP_ACK(6, "Transfer Comp Ack - 回應 PLC 任務完成確認"),
    SPARE_7(7, "Spare");

    private final int bitIndex;
    private final String description;

    WorkingBeamBitSignal(int bitIndex, String description) {
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
