package com.czkuo.rdf88701.domain.plc.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * FromCraneCommandType
 * - 封裝 From Transfer Type (W0050)
 * - 格式為：0000 000b cccc TTTT
 *   - TTTT: Command Type (4 bits)
 *   - cccc: CST Type (4 bits)
 *   - b: BCR Enable (1 bit)
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class FromCraneCommandType {

    private int raw;
    private int commandType;     // TTTT
    private int cstType;         // cccc
    private boolean bcrEnabled;  // b

    public static FromCraneCommandType fromWord(int word) {
        FromCraneCommandType result = new FromCraneCommandType();
        result.raw = word;
        result.commandType = word & 0x000F;
        result.cstType = (word >> 4) & 0x000F;
        result.bcrEnabled = ((word >> 8) & 0x1) == 1;
        return result;
    }

    public int toRaw() {
        int value = 0;
        value |= (commandType & 0x0F);          // TTTT
        value |= (cstType & 0x0F) << 4;         // cccc
        value |= (bcrEnabled ? 1 : 0) << 8;     // b
        return value;
    }

    public String getCommandName() {
        return switch (commandType) {
            case 1 -> "Move";
            case 2 -> "From";
            case 8 -> "Fetch";
            default -> "Unknown";
        };
    }

    public boolean isMove() {
        return commandType == 1;
    }

    public boolean isFrom() {
        return commandType == 2;
    }

    public boolean isFetch() {
        return commandType == 8;
    }
}
