package com.czkuo.rdf88701.domain.plc.valueobject;

import lombok.*;

/**
 * StrappingCommandMode
 * - 封裝 Strapping 的執行模式資料（W039B）
 * - 格式為：0000 0000 0000 mmmm（BCD）
 *   - mmmm: Mode（4 bits）
 *     - 0: Push to next site
 *     - 1: Need to strapping
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class StrappingCommandMode {

    private int raw;
    private int mode; // mmmm

    public static StrappingCommandMode fromWord(int word) {
        StrappingCommandMode result = new StrappingCommandMode();
        result.raw = word;
        result.mode = word & 0x000F; // 只保留最後 4 bits
        return result;
    }

    public int toRaw() {
        return mode & 0x000F;
    }

    public String getModeName() {
        return switch (mode) {
            case 0 -> "Push to next site";
            case 1 -> "Need to strapping";
            default -> "Unknown";
        };
    }

    public boolean isPush() {
        return mode == 0;
    }

    public boolean isNeedStrapping() {
        return mode == 1;
    }
}
