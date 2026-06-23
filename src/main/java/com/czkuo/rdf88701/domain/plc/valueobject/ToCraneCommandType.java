package com.czkuo.rdf88701.domain.plc.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * ToTransferType
 * - 封裝 To Transfer Type (W006F)
 * - 格式為：0000 000b cccc TTTT
 *   - TTTT: Command Type (4 bits)
 *   - cccc: CST Type (4 bits)
 *   - b: BCR Enable (預留欄位，預設為 0)
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class ToCraneCommandType {

    private int raw;
    private int commandType;     // TTTT
    private int cstType;         // cccc
    private boolean bcrEnabled;  // b（保留欄位，為對齊 FromTransferType）

    public static ToCraneCommandType fromWord(int word) {
        ToCraneCommandType result = new ToCraneCommandType();
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
            case 3 -> "To";
            default -> "Unknown";
        };
    }

    public boolean isTo() {
        return commandType == 3;
    }
}
