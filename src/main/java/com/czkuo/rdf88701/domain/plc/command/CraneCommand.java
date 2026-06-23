package com.czkuo.rdf88701.domain.plc.command;

import com.czkuo.rdf88701.domain.plc.valueobject.FromCraneCommandType;
import com.czkuo.rdf88701.domain.plc.valueobject.ToCraneCommandType;
import lombok.*;

/**
 * CraneCommand
 * - 封裝 PC → PLC Word 區控制命令內容
 * - 主要為 Transfer 指令所需之參數，如 From/To 位置與 CST 資訊
 */
@Data
public class CraneCommand {

    public enum Direction {
        FROM, TO
    }

    private Direction direction; // 👈 新增欄位：指令方向

    // === From 區資料 ===
    private FromCraneCommandType fromCraneCommandType;
    private int fromTransferNo;
    private String fromCstId;
    private int fromLocationType;
    private int fromLocationBank;
    private int fromLocationBay;
    private int fromLocationLv;

    // === To 區資料 ===
    private ToCraneCommandType toCraneCommandType;
    private int toTransferNo;
    private String toCstId;
    private int toLocationType;
    private int toLocationBank;
    private int toLocationBay;
    private int toLocationLv;

    public boolean isFromCommand() {
        return direction == Direction.FROM;
    }

    public boolean isToCommand() {
        return direction == Direction.TO;
    }

    public void cloneFrom(CraneCommand other) {
        if (other == null) return;
        this.direction = other.direction;

        this.fromCraneCommandType = other.fromCraneCommandType;
        this.fromTransferNo = other.fromTransferNo;
        this.fromCstId = other.fromCstId;
        this.fromLocationType = other.fromLocationType;
        this.fromLocationBank = other.fromLocationBank;
        this.fromLocationBay = other.fromLocationBay;
        this.fromLocationLv = other.fromLocationLv;

        this.toCraneCommandType = other.toCraneCommandType;
        this.toTransferNo = other.toTransferNo;
        this.toCstId = other.toCstId;
        this.toLocationType = other.toLocationType;
        this.toLocationBank = other.toLocationBank;
        this.toLocationBay = other.toLocationBay;
        this.toLocationLv = other.toLocationLv;
    }

    public static CraneCommand copyFrom(CraneCommand other) {
        if (other == null) return null;
        CraneCommand copy = new CraneCommand();
        copy.cloneFrom(other);
        return copy;
    }

    public boolean isDifferent(CraneCommand other) {
        if (other == null) return true;
        return this.direction != other.direction ||
                !safeEquals(this.fromCraneCommandType, other.fromCraneCommandType) ||
                this.fromTransferNo != other.fromTransferNo ||
                !safeEquals(this.fromCstId, other.fromCstId) ||
                this.fromLocationType != other.fromLocationType ||
                this.fromLocationBank != other.fromLocationBank ||
                this.fromLocationBay != other.fromLocationBay ||
                this.fromLocationLv != other.fromLocationLv ||

                !safeEquals(this.toCraneCommandType, other.toCraneCommandType) ||
                this.toTransferNo != other.toTransferNo ||
                !safeEquals(this.toCstId, other.toCstId) ||
                this.toLocationType != other.toLocationType ||
                this.toLocationBank != other.toLocationBank ||
                this.toLocationBay != other.toLocationBay ||
                this.toLocationLv != other.toLocationLv;
    }

    private boolean safeEquals(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    public String toSimpleString() {
        return String.format(
                "From[%d/%d/%d], To[%d/%d/%d], FromCST='%s', ToCST='%s', FromType=%s, ToType=%s, Direction=%s",
                fromLocationBank, fromLocationBay, fromLocationLv,
                toLocationBank, toLocationBay, toLocationLv,
                fromCstId, toCstId,
                fromCraneCommandType != null ? fromCraneCommandType.getCommandName() : "null",
                toCraneCommandType != null ? toCraneCommandType.getCommandName() : "null",
                direction != null ? direction.name() : "null"
        );
    }
}
