package com.czkuo.rdf88701.domain.plc.state.Strapping;

import com.czkuo.rdf88701.domain.plc.command.StrappingCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * StrappingCommandStatus
 * - 封裝 PC → PLC 傳送的控制狀態（Bit + Word 區資訊）
 * - 包含指令內容、即時狀態、連線與補充欄位
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class StrappingCommandStatus {

    // === Meta 資訊 ===
    private int strappingId;
    private Instant snapshotTime = Instant.now(); // 資料取得時間

    // === Bit 區資料（B0200 ~ B0207）===
    private boolean strappingReady;      // Bit 0: 表示 PC 準備完成
    private boolean strappingCmdReq;     // Bit 3: 請求執行綁帶
    private boolean strappingCompAck;    // Bit 4: 任務完成確認

    // === Word 區資料（W0398 ~ W039F）===
    private StrappingCommand command;

    // === 狀態補充欄位 ===
    private boolean complete = false;    // 是否完成組合（Bit + Word）
    private boolean available = true;    // 是否有效
    private boolean stale = false;       // 是否為過時資料

    // === 通訊狀態欄位 ===
    private boolean connected;
    private Instant lastConnectedTime;
    private Instant lastDisconnectedTime;

    // === 指令快照欄位（最近一次寫入的內容）===
    private StrappingCommandStatus lastWriteCommand;

    // =====================================================================================
    // 工具方法區
    // =====================================================================================

    public boolean isOverdue(long thresholdSeconds) {
        return snapshotTime == null || Duration.between(snapshotTime, Instant.now()).getSeconds() > thresholdSeconds;
    }

    public boolean isFullyCombined() {
        return complete;
    }

    public boolean isValidAndComplete(long thresholdSeconds) {
        return available && !isOverdue(thresholdSeconds) && complete;
    }

    public String toSimpleString() {
        return String.format(
                "Strapping#%d - Mode='%s', Count=%s, Ready=%s",
                strappingId,
                getOrDefault(() -> command.getStrappingMode().getModeName(), "null"),
                getOrDefault(() -> String.valueOf(command.getStrappingCount()), "null"),
                strappingReady ? "Y" : "N"
        );
    }

    private <T> T getOrDefault(SupplierWithException<T> supplier, T defaultValue) {
        try {
            T result = supplier.get();
            return result != null ? result : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @FunctionalInterface
    public interface SupplierWithException<T> {
        T get() throws Exception;
    }

    // =====================================================================================
    // 複製與比對邏輯
    // =====================================================================================

    public void cloneContentFrom(StrappingCommandStatus bit, StrappingCommandStatus word) {
        if (bit != null) {
            this.strappingReady = bit.strappingReady;
            this.strappingCmdReq = bit.strappingCmdReq;
            this.strappingCompAck = bit.strappingCompAck;
        }
        if (word != null && word.command != null) {
            this.command = StrappingCommand.copyFrom(word.command);
        }
    }

    public void cloneContentFrom(StrappingCommandStatus source) {
        if (source == null) return;

        this.strappingId = source.strappingId;
        this.snapshotTime = source.snapshotTime;
        this.strappingReady = source.strappingReady;
        this.strappingCmdReq = source.strappingCmdReq;
        this.strappingCompAck = source.strappingCompAck;

        this.command = StrappingCommand.copyFrom(source.command);
        this.complete = source.complete;
        this.available = source.available;
        this.stale = source.stale;

        this.connected = source.connected;
        this.lastConnectedTime = source.lastConnectedTime;
        this.lastDisconnectedTime = source.lastDisconnectedTime;

        this.lastWriteCommand = StrappingCommandStatus.copyFrom(source.lastWriteCommand);
    }

    public boolean isContentDifferent(StrappingCommandStatus other) {
        if (other == null) return true;

        if (this.strappingReady != other.strappingReady ||
                this.strappingCmdReq != other.strappingCmdReq ||
                this.strappingCompAck != other.strappingCompAck) {
            return true;
        }

        if (this.command == null) return other.command != null;
        return !this.command.equals(other.command);
    }

    public boolean hasMeaningfulChange(StrappingCommandStatus other) {
        return isContentDifferent(other);
    }

    public void setConnectedFromPlcStatus(boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        this.connected = plcConnected;
        this.lastConnectedTime = lastConnected;
        this.lastDisconnectedTime = lastDisconnected;
    }

    public static StrappingCommandStatus withConnectionInfo(StrappingCommandStatus status, boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        status.setConnectedFromPlcStatus(plcConnected, lastConnected, lastDisconnected);
        return status;
    }

    public static StrappingCommandStatus copyFrom(StrappingCommandStatus source) {
        if (source == null) return null;
        StrappingCommandStatus copy = new StrappingCommandStatus();
        copy.cloneContentFrom(source);
        return copy;
    }

    public Integer getStrappingNo() {
        return command != null ? command.getStrappingNo() : null;
    }
}
