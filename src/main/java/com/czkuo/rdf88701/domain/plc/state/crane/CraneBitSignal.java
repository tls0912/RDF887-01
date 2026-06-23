package com.czkuo.rdf88701.domain.plc.state.crane;

/**
 * Crane PLC Bit Signal 定義
 * - 專供 PC → PLC Bit 指令 (ROBOT Indicate Bit Data)
 */
public enum CraneBitSignal {

    // ==========================
    // Transfer Job Handshake
    // ==========================
    TRANSFER_READY(0, "Transfer Ready"),
    FROM_TRANSFER_CMD_REQ(1, "From Transfer Cmd Req"),
    FROM_TRANSFER_COMP_ACK(2, "From Transfer Comp Ack"),
    TO_TRANSFER_CMD_REQ(3, "To Transfer Cmd Req"),
    TO_TRANSFER_COMP_ACK(4, "To Transfer Comp Ack"),

    // ==========================
    // Home Return Handshake
    // ==========================
    HOME_RETURN_REQUEST(7, "Home Return Request"),
    REMOVE_ACCOUNT_ACK(8, "Remove Account Ack"),

    // ==========================
    // 其他位元 Spare (預留空位，未來擴充)
    // ==========================
    SPARE_5(5, "Spare"),
    SPARE_6(6, "Spare"),
    SPARE_9(9, "Spare"),
    SPARE_10(10, "Spare"),
    SPARE_11(11, "Spare"),
    SPARE_12(12, "Spare"),
    SPARE_13(13, "Spare"),
    SPARE_14(14, "Spare"),
    SPARE_15(15, "Spare"),
    SPARE_16(16, "Spare"),
    SPARE_17(17, "Spare"),
    SPARE_18(18, "Spare"),
    SPARE_19(19, "Spare"),
    SPARE_20(20, "Spare"),
    SPARE_21(21, "Spare"),
    SPARE_22(22, "Spare"),
    SPARE_23(23, "Spare"),
    SPARE_24(24, "Spare"),
    SPARE_25(25, "Spare"),
    SPARE_26(26, "Spare"),
    SPARE_27(27, "Spare"),
    SPARE_28(28, "Spare"),
    SPARE_29(29, "Spare"),
    SPARE_30(30, "Spare"),
    SPARE_31(31, "Spare");

    private final int bitIndex;
    private final String description;

    CraneBitSignal(int bitIndex, String description) {
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
