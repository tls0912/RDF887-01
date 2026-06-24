package com.czkuo.rdf88701.domain.plc.state.crane;

import com.czkuo.rdf88701.domain.plc.command.CraneCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * CraneCommandStatus
 * - 封裝 PC → PLC 的控制狀態（Bit + Word）
 * - 包含指令狀態、連線資訊、補充狀態
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class CraneCommandStatus {

    // === Meta 資訊 ===
    private int craneId;
    private Instant snapshotTime = Instant.now();

    // === Bit 區狀態 (B0030 ~ B004F) ===
    private boolean transferReady;
    private boolean fromTransferCmdReq;
    private boolean fromTransferCompAck;
    private boolean toTransferCmdReq;
    private boolean toTransferCompAck;
    private boolean homeReturnRequest;
    private boolean removeAccountAck;

    // === Word 區指令資料 (W0050 ~ W008F) ===
    private CraneCommand command;

    // === 狀態補充欄位 ===
    private boolean complete = false;
    private boolean available = true;
    private boolean stale = false;

    // === 通訊狀態資訊 ===
    private boolean connected;
    private Instant lastConnectedTime;
    private Instant lastDisconnectedTime;

    // === 額外補充欄位 ===
    private CraneCommandStatus lastWriteCommand;

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
        return available && !isOverdue(thresholdSeconds) && isFullyCombined();
    }

    public String toSimpleString() {
        return String.format(
                "CraneCmd#%d - From[%d/%d/%d], To[%d/%d/%d], FromCST='%s', ToCST='%s', FromType=%s, ToType=%s, Ready=%s",
                craneId,
                getOrDefault(() -> command.getFromLocationBank(), -1),
                getOrDefault(() -> command.getFromLocationBay(), -1),
                getOrDefault(() -> command.getFromLocationLv(), -1),
                getOrDefault(() -> command.getToLocationBank(), -1),
                getOrDefault(() -> command.getToLocationBay(), -1),
                getOrDefault(() -> command.getToLocationLv(), -1),
                getOrDefault(() -> command.getFromCstId(), "null"),
                getOrDefault(() -> command.getToCstId(), "null"),
                getOrDefault(() -> command.getFromCraneCommandType().getCommandName(), "null"),
                getOrDefault(() -> command.getToCraneCommandType().getCommandName(), "null"),
                transferReady ? "Y" : "N"
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

    public void cloneContentFrom(CraneCommandStatus bit, CraneCommandStatus word) {
        if (bit != null) {
            this.transferReady = bit.transferReady;
            this.fromTransferCmdReq = bit.fromTransferCmdReq;
            this.fromTransferCompAck = bit.fromTransferCompAck;
            this.toTransferCmdReq = bit.toTransferCmdReq;
            this.toTransferCompAck = bit.toTransferCompAck;
            this.homeReturnRequest = bit.homeReturnRequest;
            this.removeAccountAck = bit.removeAccountAck;
        }
        if (word != null && word.command != null) {
            this.command = CraneCommand.copyFrom(word.command);
        }
    }

    public void cloneContentFrom(CraneCommandStatus source) {
        if (source == null) return;

        this.craneId = source.craneId;
        this.snapshotTime = source.snapshotTime;
        this.transferReady = source.transferReady;
        this.fromTransferCmdReq = source.fromTransferCmdReq;
        this.fromTransferCompAck = source.fromTransferCompAck;
        this.toTransferCmdReq = source.toTransferCmdReq;
        this.toTransferCompAck = source.toTransferCompAck;
        this.homeReturnRequest = source.homeReturnRequest;
        this.removeAccountAck = source.removeAccountAck;

        this.command = CraneCommand.copyFrom(source.command);
        this.complete = source.complete;
        this.available = source.available;
        this.stale = source.stale;

        this.connected = source.connected;
        this.lastConnectedTime = source.lastConnectedTime;
        this.lastDisconnectedTime = source.lastDisconnectedTime;

        this.lastWriteCommand = CraneCommandStatus.copyFrom(source.lastWriteCommand);
    }

    public boolean hasMeaningfulChange(CraneCommandStatus other) {
        return isContentDifferent(other);
    }

    public boolean isContentDifferent(CraneCommandStatus other) {
        if (other == null) return true;
        if (this.transferReady != other.transferReady ||
                this.fromTransferCmdReq != other.fromTransferCmdReq ||
                this.fromTransferCompAck != other.fromTransferCompAck ||
                this.toTransferCmdReq != other.toTransferCmdReq ||
                this.toTransferCompAck != other.toTransferCompAck ||
                this.homeReturnRequest != other.homeReturnRequest ||
                this.removeAccountAck != other.removeAccountAck) {
            return true;
        }

        if (this.command == null) return other.command != null;
        return !this.command.equals(other.command);
    }

    public void setConnectedFromPlcStatus(boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        this.connected = plcConnected;
        this.lastConnectedTime = lastConnected;
        this.lastDisconnectedTime = lastDisconnected;
    }

    public static CraneCommandStatus withConnectionInfo(CraneCommandStatus status, boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        status.setConnectedFromPlcStatus(plcConnected, lastConnected, lastDisconnected);
        return status;
    }

    public static CraneCommandStatus copyFrom(CraneCommandStatus source) {
        if (source == null) return null;
        CraneCommandStatus copy = new CraneCommandStatus();
        copy.cloneContentFrom(source);
        return copy;
    }

    public Integer getFromTransferNo() {
        return command != null ? command.getFromTransferNo() : null;
    }

    public Integer getToTransferNo() {
        return command != null ? command.getToTransferNo() : null;
    }
}
