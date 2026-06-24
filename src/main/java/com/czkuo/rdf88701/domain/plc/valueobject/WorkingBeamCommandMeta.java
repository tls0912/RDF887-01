package com.czkuo.rdf88701.domain.plc.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * WorkingBeamCommandMeta
 * - 封裝 Working Beam 的進出方向控制欄位（目前僅含 direction）
 * - 格式為：0000 0000 0000 00dd（可擴充）
 *   - dd: direction (BCD)
 *     - 1: IN
 *     - 2: OUT
 *   - 其餘保留位元未使用
 *
 * 2026-06-24 狀態：已修改，註解已依現有實作校正。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class WorkingBeamCommandMeta {

    private int raw;
    private int direction; // dd

    public static WorkingBeamCommandMeta fromWord(int word) {
        WorkingBeamCommandMeta meta = new WorkingBeamCommandMeta();
        meta.raw = word;
        meta.direction = word & 0x000F;
        return meta;
    }

    public int toRaw() {
        return direction & 0x000F;
    }

    public String getDirectionDescription() {
        return switch (direction) {
            case 1 -> "IN";
            case 2 -> "OUT";
            default -> "UNKNOWN";
        };
    }

    public boolean isIn() {
        return direction == 1;
    }

    public boolean isOut() {
        return direction == 2;
    }
}
