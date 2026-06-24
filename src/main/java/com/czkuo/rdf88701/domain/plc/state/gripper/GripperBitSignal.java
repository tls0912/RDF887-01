package com.czkuo.rdf88701.domain.plc.state.gripper;

/**
 * Gripper PLC Bit Signal 定義
 * - 對應 PLC B0200 ~ B0207（每個 Gripper 起始位址不同，由 Registry 控制）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public enum GripperBitSignal {

    GRIPPER_READY(0, "Gripper Ready (Enable/Disable)"), // B0200
    SPARE_1(1, "Spare"),                                // B0201
    REMOVE_ACCOUNT_ACK(2, "Remove Account Ack"),        // B0202
    SPARE_3(3, "Spare"),                                // B0203
    SPARE_4(4, "Spare"),                                // B0204
    GRIPPER_CMD_REQ(5, "Gripper CMD Req"),              // B0205
    GRIPPER_COMP_ACK(6, "Gripper Comp Ack"),            // B0206
    SPARE_7(7, "Spare");                                // B0207

    private final int bitIndex;
    private final String description;

    GripperBitSignal(int bitIndex, String description) {
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
