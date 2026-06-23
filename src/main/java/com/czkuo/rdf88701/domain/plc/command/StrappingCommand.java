package com.czkuo.rdf88701.domain.plc.command;

import com.czkuo.rdf88701.domain.plc.valueobject.StrappingCommandMode;
import lombok.Data;

/**
 * StrappingCommand
 * - 封裝 PC → PLC Word 區控制命令內容（W0398 ~ W039F）
 * - 包含 Strapping No、Count、Mode
 */
@Data
public class StrappingCommand {

    private int strappingNo;                      // 綁帶任務編號（W0398）
    private int strappingCount;                   // 綁帶次數 / 數量（W039A）
    private StrappingCommandMode strappingMode;   // 執行模式（W039B）

    public void cloneFrom(StrappingCommand other) {
        if (other == null) return;
        this.strappingNo = other.strappingNo;
        this.strappingCount = other.strappingCount;
        this.strappingMode = other.strappingMode != null
                ? StrappingCommandMode.fromWord(other.strappingMode.toRaw())
                : null;
    }

    public static StrappingCommand copyFrom(StrappingCommand other) {
        if (other == null) return null;
        StrappingCommand copy = new StrappingCommand();
        copy.cloneFrom(other);
        return copy;
    }

    public boolean isDifferent(StrappingCommand other) {
        if (other == null) return true;
        return this.strappingNo != other.strappingNo ||
                this.strappingCount != other.strappingCount ||
                !safeEquals(this.strappingMode, other.strappingMode);
    }

    private boolean safeEquals(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    public String toSimpleString() {
        return String.format(
                "SNo=%d, Count=%d, Mode=%s",
                strappingNo,
                strappingCount,
                strappingMode != null ? strappingMode.getModeName() : "null"
        );
    }
}
