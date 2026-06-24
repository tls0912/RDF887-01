package com.czkuo.rdf88701.domain.plc.command;

import com.czkuo.rdf88701.domain.plc.valueobject.GripperCommandType;
import lombok.Data;

/**
 * GripperCommand
 * - 封裝 PC → PLC 的 Write Word 區控制命令內容（W0260 ~ W027E）
 * - 包含 Transfer No、Command Type、Tray Height、Location、Product ID 等資訊
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class GripperCommand {

    private int transferNo;                           // 指令編號（W0260）
    private GripperCommandType taskType;              // 指令類型（W0261）
    private int trayHeight;                           // Tray 高度（W0262）
    private int locationBank;                         // 儲位 Bank 值（W0263）
    private int locationBay;                          // 儲位 Bay 值（W0264）
    private int locationLevel;                        // 儲位 Level 值（W0265）
    private String productId;                         // 產品條碼（W0266 ~ W027E，共 50 字元）

    /**
     * 判斷是否為 MOVE 指令（移動作業）
     */
    public boolean isMoveCommand() {
        return taskType != null && taskType.isMove();
    }

    /**
     * 判斷是否為 PICK 指令（取出動作）
     */
    public boolean isPickCommand() {
        return taskType != null && taskType.isPick();
    }

    /**
     * 判斷是否為 DROP 指令（放入動作）
     */
    public boolean isDropCommand() {
        return taskType != null && taskType.isDrop();
    }

    /**
     * 複製另一筆 GripperCommand 的內容
     */
    public void cloneFrom(GripperCommand other) {
        if (other == null) return;

        this.transferNo = other.transferNo;
        this.taskType = other.taskType != null ? GripperCommandType.fromWord(other.taskType.toRaw()) : null;
        this.trayHeight = other.trayHeight;
        this.locationBank = other.locationBank;
        this.locationBay = other.locationBay;
        this.locationLevel = other.locationLevel;
        this.productId = other.productId;
    }

    /**
     * 建立副本
     */
    public static GripperCommand copyFrom(GripperCommand other) {
        if (other == null) return null;
        GripperCommand copy = new GripperCommand();
        copy.cloneFrom(other);
        return copy;
    }

    /**
     * 比對兩筆指令是否不同
     */
    public boolean isDifferent(GripperCommand other) {
        if (other == null) return true;

        return this.transferNo != other.transferNo ||
                !safeEquals(this.taskType, other.taskType) ||
                this.trayHeight != other.trayHeight ||
                this.locationBank != other.locationBank ||
                this.locationBay != other.locationBay ||
                this.locationLevel != other.locationLevel ||
                !safeEquals(this.productId, other.productId);
    }

    /**
     * 安全比較（避免 NullPointer）
     */
    private boolean safeEquals(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    /**
     * 簡要文字輸出（Log / Debug 用）
     */
    public String toSimpleString() {
        return String.format(
                "TNo=%d, CmdType=%s, Tray=%.1fmm, Bank/Bay/Lv=%d/%d/%d, Product='%s'",
                transferNo,
                taskType != null ? taskType.getCommandName() : "null",
                trayHeight / 100.0,
                locationBank, locationBay, locationLevel,
                productId != null ? productId : ""
        );
    }
}
