package com.czkuo.rdf88701.domain.plc.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * WorkingBeamCommandType
 * - 封裝 Working Beam 的 Transfer Type（W0221）
 * - 格式為：0000 0000 0000 TTTT（BCD）
 *   - TTTT: Command Type（4 bits）
 *     - 1: MOVE  - 執行移動作業
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class WorkingBeamCommandType {

    private int raw;
    private int commandType; // TTTT

    public static WorkingBeamCommandType fromWord(int word) {
        WorkingBeamCommandType result = new WorkingBeamCommandType();
        result.raw = word;
        result.commandType = word & 0x000F; // 只保留最後4 bits
        return result;
    }

    public int toRaw() {
        return commandType & 0x000F; // 只輸出 4 bits 指令碼
    }

    public String getCommandName() {
        return switch (commandType) {
            case 1 -> "Move";
            default -> "Unknown";
        };
    }

    public boolean isMove() {
        return commandType == 1;
    }
}
