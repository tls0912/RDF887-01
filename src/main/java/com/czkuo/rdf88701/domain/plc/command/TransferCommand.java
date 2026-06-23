package com.czkuo.rdf88701.domain.plc.command;

import com.czkuo.rdf88701.domain.plc.valueobject.TransferCommandType;
import lombok.Data;

/**
 * TransferCommand
 * - 封裝 PC → PLC Word 區控制命令內容（W0100 ~ W011E）
 * - 包含 Transfer No、Command Type、Location、Product ID 等資訊
 */
@Data
public class TransferCommand {

    private int transferNo;                        // 指令編號（W0100）
    private TransferCommandType taskType;          // 指令類型（W0101）
    private int locationBank;                      // 儲位 Bank 值 (W0103)
    private int locationBay;                       // 儲位 Bay 值 (W0104)
    private int locationLevel;                     // 儲位 Level 值 (W0105)
    private String productId;                      // 產品條碼（W0106 ~ W011E，共 50 字元）

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
     * 複製另一筆 TransferCommand 的內容
     */
    public void cloneFrom(TransferCommand other) {
        if (other == null) return;

        this.transferNo = other.transferNo;
        this.taskType = other.taskType != null ? TransferCommandType.fromWord(other.taskType.toRaw()) : null;
        this.locationBank = other.locationBank;
        this.locationBay = other.locationBay;
        this.locationLevel = other.locationLevel;
        this.productId = other.productId;
    }

    /**
     * 建立副本
     */
    public static TransferCommand copyFrom(TransferCommand other) {
        if (other == null) return null;
        TransferCommand copy = new TransferCommand();
        copy.cloneFrom(other);
        return copy;
    }

    /**
     * 檢查兩筆指令是否不同
     */
    public boolean isDifferent(TransferCommand other) {
        if (other == null) return true;

        return this.transferNo != other.transferNo ||
                !safeEquals(this.taskType, other.taskType) ||
                this.locationBank != other.locationBank ||
                this.locationBay != other.locationBay ||
                this.locationLevel != other.locationLevel ||
                !safeEquals(this.productId, other.productId);
    }

    /**
     * 安全比較兩個物件（處理 null 的情況）
     */
    private boolean safeEquals(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    /**
     * 簡易文字輸出，用於 log / debug 顯示
     */
    public String toSimpleString() {
        return String.format(
                "TNo=%d, CmdType=%s, Bank/Bay/Lv=%d/%d/%d, Product='%s'",
                transferNo,
                taskType != null ? taskType.getCommandName() : "null",
                locationBank, locationBay, locationLevel,
                productId != null ? productId : ""
        );
    }
}
