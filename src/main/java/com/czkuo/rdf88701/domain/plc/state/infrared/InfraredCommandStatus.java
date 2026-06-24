package com.czkuo.rdf88701.domain.plc.state.infrared;

import com.czkuo.rdf88701.domain.plc.command.InfraredCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * InfraredCommandStatus
 * - 封裝 PC → PLC 傳送給紅外線設備的指令控制狀態（Bit + Word 區資訊）
 * - 結合指令內容、即時狀態、可用性、通訊狀態與快照記錄
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class InfraredCommandStatus {

    // === Meta 資訊 ===
    private int infraredId;
    private Instant snapshotTime = Instant.now(); // 資料取得時間

    // === Bit 區資料（B01C8 ~ B01CF）===
    private boolean infraredReady;       // Bit 0: 紅外線設備 Ready 狀態
    private boolean measureCmdReq;       // Bit 4: 測高請求
    private boolean measureCompAck;      // Bit 5: 測高完成確認

    // === Word 區資料（W0360 ~ W0367）===
    private InfraredCommand command;

    // === 狀態補充欄位 ===
    private boolean complete = false;    // 是否完成 Bit + Word 組合
    private boolean available = true;    // 是否有效
    private boolean stale = false;       // 是否為過時資料

    // === 通訊狀態欄位 ===
    private boolean connected;
    private Instant lastConnectedTime;
    private Instant lastDisconnectedTime;

    // === 指令快照欄位（最近一次寫入的內容）===
    private InfraredCommandStatus lastWriteCommand;

    // =====================================================================================
    // 判斷與比對邏輯
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
                "Infrared#%d - Cmd='%s', TrayThickness=%.2f, Ready=%s",
                infraredId,
                getOrDefault(() -> String.valueOf(command.getInfraredNo()), "null"),
                getOrDefault(() -> command.getTrayThickness() / 100.0, 0.0),
                infraredReady ? "Y" : "N"
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

    public void cloneContentFrom(InfraredCommandStatus bit, InfraredCommandStatus word) {
        if (bit != null) {
            this.infraredReady = bit.infraredReady;
            this.measureCmdReq = bit.measureCmdReq;
            this.measureCompAck = bit.measureCompAck;
        }
        if (word != null && word.command != null) {
            this.command = InfraredCommand.copyFrom(word.command);
        }
    }

    public void cloneContentFrom(InfraredCommandStatus source) {
        if (source == null) return;

        this.infraredId = source.infraredId;
        this.snapshotTime = source.snapshotTime;
        this.infraredReady = source.infraredReady;
        this.measureCmdReq = source.measureCmdReq;
        this.measureCompAck = source.measureCompAck;

        this.command = InfraredCommand.copyFrom(source.command);
        this.complete = source.complete;
        this.available = source.available;
        this.stale = source.stale;

        this.connected = source.connected;
        this.lastConnectedTime = source.lastConnectedTime;
        this.lastDisconnectedTime = source.lastDisconnectedTime;

        this.lastWriteCommand = InfraredCommandStatus.copyFrom(source.lastWriteCommand);
    }

    public boolean isContentDifferent(InfraredCommandStatus other) {
        if (other == null) return true;

        if (this.infraredReady != other.infraredReady ||
                this.measureCmdReq != other.measureCmdReq ||
                this.measureCompAck != other.measureCompAck) {
            return true;
        }

        if (this.command == null) return other.command != null;
        return !this.command.equals(other.command);
    }

    public boolean hasMeaningfulChange(InfraredCommandStatus other) {
        return isContentDifferent(other);
    }

    public void setConnectedFromPlcStatus(boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        this.connected = plcConnected;
        this.lastConnectedTime = lastConnected;
        this.lastDisconnectedTime = lastDisconnected;
    }

    public static InfraredCommandStatus withConnectionInfo(InfraredCommandStatus status, boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        status.setConnectedFromPlcStatus(plcConnected, lastConnected, lastDisconnected);
        return status;
    }

    public static InfraredCommandStatus copyFrom(InfraredCommandStatus source) {
        if (source == null) return null;
        InfraredCommandStatus copy = new InfraredCommandStatus();
        copy.cloneContentFrom(source);
        return copy;
    }

    public Integer getInfraredNo() {
        return command != null ? command.getInfraredNo() : null;
    }
}
