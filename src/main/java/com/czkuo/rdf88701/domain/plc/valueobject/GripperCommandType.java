package com.czkuo.rdf88701.domain.plc.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * GripperCommandType
 * - 封裝 Gripper 指令中的 Transfer Type 欄位（W0261）
 * - 格式為：0000 0000 0000 TTTT（BCD）
 *   - TTTT: 指令類型（4 bits）
 *     - 1: MOVE  - 執行搬運
 *     - 2: PICK  - 取料
 *     - 3: DROP  - 放料
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class GripperCommandType {

    private int raw;
    private int commandType; // TTTT

    /**
     * 從 Word 整數解析出 GripperCommandType
     */
    public static GripperCommandType fromWord(int word) {
        GripperCommandType result = new GripperCommandType();
        result.raw = word;
        result.commandType = word & 0x000F; // 僅取最低 4 bits（TTTT）
        return result;
    }

    /**
     * 轉回原始 Word（BCD 編碼）
     */
    public int toRaw() {
        return commandType & 0x000F;
    }

    /**
     * 取得對應的文字名稱
     */
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
