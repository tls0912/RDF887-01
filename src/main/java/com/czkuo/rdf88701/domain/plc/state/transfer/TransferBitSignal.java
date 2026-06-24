package com.czkuo.rdf88701.domain.plc.state.transfer;

/**
 * Transfer PLC Bit Signal 定義
 * - 實作對應 PLC B0100 ~ B0107 區段
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public enum TransferBitSignal {

    TRANSFER_READY(0, "Transfer Ready (Enable/Disable)"), // B0100
    SPARE_1(1, "Spare"),                                  // B0101
    REMOVE_ACCOUNT_ACK(2, "Remove Account Ack"),          // B0102
    SPARE_3(3, "Spare"),                                  // B0103
    SPARE_4(4, "Spare"),                                  // B0104
    TRANSFER_CMD_REQ(5, "Transfer CMD Req"),              // B0105
    TRANSFER_COMP_ACK(6, "Transfer Comp Ack"),            // B0106
    SPARE_7(7, "Spare");                                  // B0107

    private final int bitIndex;
    private final String description;

    TransferBitSignal(int bitIndex, String description) {
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
