package com.czkuo.rdf88701.domain.plc.state.workingbeam;

import com.czkuo.rdf88701.domain.plc.command.WorkingBeamCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * WorkingBeamCommandStatus
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
public class WorkingBeamCommandStatus {

    // === Meta 資訊 ===
    private int workingBeamId;
    private Instant snapshotTime = Instant.now(); // 資料取得時間

    // === Bit 區資料（B1000 ~ B10FF）===
    private boolean transferReady;      // Bit 0: 表示 PC 已準備好可接收任務
    private boolean transferCmdReq;     // Bit 5: 請求 PLC 執行搬運
    private boolean transferCompAck;    // Bit 6: 回應 PLC 任務完成

    // === Word 區資料（W1100 ~ W11FF）===
    private WorkingBeamCommand command;

    // === 狀態補充欄位 ===
    private boolean complete = false;   // 是否完成組合（Bit + Word）
    private boolean available = true;   // 是否有效
    private boolean stale = false;      // 是否為過時資料

    // === 通訊狀態欄位 ===
    private boolean connected;
    private Instant lastConnectedTime;
    private Instant lastDisconnectedTime;

    // === 指令快照欄位（最近一次寫入的內容）===
    private WorkingBeamCommandStatus lastWriteCommand;

    // =====================================================================================
    // 工具方法區
    // =====================================================================================

    /**
     * 判斷此狀態是否過期（根據 snapshotTime）
     */
    public boolean isOverdue(long thresholdSeconds) {
        return snapshotTime == null || Duration.between(snapshotTime, Instant.now()).getSeconds() > thresholdSeconds;
    }

    /**
     * 是否已完成 Bit + Word 組合
     */
    public boolean isFullyCombined() {
        return complete;
    }

    /**
     * 檢查是否為有效且完成的資料
     */
    public boolean isValidAndComplete(long thresholdSeconds) {
        return available && !isOverdue(thresholdSeconds) && complete;
    }

    /**
     * 顯示簡易字串
     */
    public String toSimpleString() {
        return String.format(
                "WB#%d - Cmd='%s', Dir='%s', Ready=%s",
                workingBeamId,
                getOrDefault(() -> command.getCommandType().getCommandName(), "null"),
                getOrDefault(() -> command.getCommandMeta().getDirectionDescription(), "null"),
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

    // =====================================================================================
    // 複製與比對邏輯
    // =====================================================================================

    /**
     * 合併 Bit 與 Word 區狀態
     */
    public void cloneContentFrom(WorkingBeamCommandStatus bit, WorkingBeamCommandStatus word) {
        if (bit != null) {
            this.transferReady = bit.transferReady;
            this.transferCmdReq = bit.transferCmdReq;
            this.transferCompAck = bit.transferCompAck;
        }
        if (word != null && word.command != null) {
            this.command = WorkingBeamCommand.copyFrom(word.command);
        }
    }

    /**
     * 完整複製來源狀態
     */
    public void cloneContentFrom(WorkingBeamCommandStatus source) {
        if (source == null) return;

        this.workingBeamId = source.workingBeamId;
        this.snapshotTime = source.snapshotTime;
        this.transferReady = source.transferReady;
        this.transferCmdReq = source.transferCmdReq;
        this.transferCompAck = source.transferCompAck;

        this.command = WorkingBeamCommand.copyFrom(source.command);
        this.complete = source.complete;
        this.available = source.available;
        this.stale = source.stale;

        this.connected = source.connected;
        this.lastConnectedTime = source.lastConnectedTime;
        this.lastDisconnectedTime = source.lastDisconnectedTime;

        this.lastWriteCommand = WorkingBeamCommandStatus.copyFrom(source.lastWriteCommand);
    }

    /**
     * 檢查內容是否不同
     */
    public boolean isContentDifferent(WorkingBeamCommandStatus other) {
        if (other == null) return true;

        if (this.transferReady != other.transferReady ||
                this.transferCmdReq != other.transferCmdReq ||
                this.transferCompAck != other.transferCompAck) {
            return true;
        }

        if (this.command == null) return other.command != null;
        return !this.command.equals(other.command);
    }

    public boolean hasMeaningfulChange(WorkingBeamCommandStatus other) {
        return isContentDifferent(other);
    }

    /**
     * 設定通訊連線資訊
     */
    public void setConnectedFromPlcStatus(boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        this.connected = plcConnected;
        this.lastConnectedTime = lastConnected;
        this.lastDisconnectedTime = lastDisconnected;
    }

    /**
     * 建立帶有通訊資訊的新狀態
     */
    public static WorkingBeamCommandStatus withConnectionInfo(WorkingBeamCommandStatus status, boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        status.setConnectedFromPlcStatus(plcConnected, lastConnected, lastDisconnected);
        return status;
    }

    /**
     * 建立副本
     */
    public static WorkingBeamCommandStatus copyFrom(WorkingBeamCommandStatus source) {
        if (source == null) return null;
        WorkingBeamCommandStatus copy = new WorkingBeamCommandStatus();
        copy.cloneContentFrom(source);
        return copy;
    }

    public Integer getTransferNo() {
        return command != null ? command.getTransferNo() : null;
    }
}
