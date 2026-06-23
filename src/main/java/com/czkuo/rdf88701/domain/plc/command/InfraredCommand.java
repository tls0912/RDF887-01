package com.czkuo.rdf88701.domain.plc.command;

import lombok.Data;

/**
 * InfraredCommand
 * - 封裝 PC → PLC Word 區控制命令內容（W0360 ~ W0367）
 * - 包含紅外線設備編號、托盤厚度等資訊
 */
@Data
public class InfraredCommand {

    private int infraredNo;       // 紅外線裝置編號（W0360）
    private int trayThickness;    // 托盤厚度（W0362），單位為 0.01mm，例如 5.62mm → 傳 562

    /**
     * 判斷是否資料相同
     */
    public boolean isSameAs(InfraredCommand other) {
        if (other == null) return false;
        return this.infraredNo == other.infraredNo &&
                this.trayThickness == other.trayThickness;
    }

    /**
     * 複製來源指令內容
     */
    public void cloneFrom(InfraredCommand other) {
        if (other == null) return;
        this.infraredNo = other.infraredNo;
        this.trayThickness = other.trayThickness;
    }

    /**
     * 建立副本
     */
    public static InfraredCommand copyFrom(InfraredCommand other) {
        if (other == null) return null;
        InfraredCommand copy = new InfraredCommand();
        copy.cloneFrom(other);
        return copy;
    }

    /**
     * 轉為簡易字串顯示
     */
    public String toSimpleString() {
        return String.format(
                "InfraredNo=%d, TrayThickness=%.2fmm",
                infraredNo,
                trayThickness / 100.0
        );
    }
}