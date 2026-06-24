package com.czkuo.rdf88701.domain.plc.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * TransferCommandType
 * - 封裝 Transfer 指令的 Transfer Type（W0101）
 * - 格式為：0000 0000 0000 TTTT（BCD）
 *   - TTTT: Command Type（4 bits）
 *     - 1: MOVE  - 執行搬運移動
 *     - 2: PICK  - 執行取貨
 *     - 3: DROP  - 執行放貨
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class TransferCommandType {

    private int raw;
    private int commandType; // TTTT

    public static TransferCommandType fromWord(int word) {
        TransferCommandType result = new TransferCommandType();
        result.raw = word;
        result.commandType = word & 0x000F; // 保留最低 4 bits
        return result;
    }

    public int toRaw() {
        return commandType & 0x000F;
    }

    public String getCommandName() {
        return switch (commandType) {
            case 1 -> "MOVE";
            case 2 -> "PICK";
            case 3 -> "DROP";
            default -> "UNKNOWN";
        };
    }

    public boolean isMove() {
        return commandType == 1;
    }

    public boolean isPick() {
        return commandType == 2;
    }

    public boolean isDrop() {
        return commandType == 3;
    }
}
