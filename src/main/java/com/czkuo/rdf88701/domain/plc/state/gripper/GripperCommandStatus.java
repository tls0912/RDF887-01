package com.czkuo.rdf88701.domain.plc.state.gripper;

import com.czkuo.rdf88701.domain.plc.command.GripperCommand;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * GripperCommandStatus
 * - 封裝 PC → PLC 傳送給 Gripper 的控制狀態（Write Bit + Write Word）
 * - 包含指令內容、即時旗標、連線補充資訊等
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class GripperCommandStatus {

    // === Meta 資訊 ===
    private int gripperId;
    private Instant snapshotTime = Instant.now(); // 資料取得時間

    // === Bit 區資料（B0188 ~ B018F）===
    private boolean transferReady;        // Bit 0: Gripper Ready（啟用裝置）
    private boolean removeAccountAck;     // Bit 2: 回應 PLC 刪除在籍確認
    private boolean transferCmdReq;       // Bit 5: 請求 PLC 執行 Transfer 任務
    private boolean transferCompAck;      // Bit 6: 任務完成確認（Comp Ack）

    // === Word 區資料（W0260 ~ W027F）===
    private GripperCommand command;

    // === 狀態補充欄位 ===
    private boolean complete = false;    // 是否已組合完成（Bit + Word）
    private boolean available = true;    // 是否為有效資料
    private boolean stale = false;       // 是否過期

    // === 通訊狀態資訊 ===
    private boolean connected;
    private Instant lastConnectedTime;
    private Instant lastDisconnectedTime;

    // === 上一次寫入的指令快照 ===
    private GripperCommandStatus lastWriteCommand;

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
     * 是否 Bit + Word 都已組合完成
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
     * 簡要文字表示
     */
    public String toSimpleString() {
        return String.format(
                "Gripper#%d - Cmd='%s', Loc=%d-%d-%d, Product='%s', Ready=%s",
                gripperId,
                getOrDefault(() -> command.getTaskType().getCommandName(), "null"),
                getOrDefault(() -> command.getLocationBank(), -1),
                getOrDefault(() -> command.getLocationBay(), -1),
                getOrDefault(() -> command.getLocationLevel(), -1),
                getOrDefault(() -> command.getProductId(), ""),
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
     * 合併 Bit 與 Word 狀態資料
     */
    public void cloneContentFrom(GripperCommandStatus bit, GripperCommandStatus word) {
        if (bit != null) {
            this.transferReady = bit.transferReady;
            this.removeAccountAck = bit.removeAccountAck;
            this.transferCmdReq = bit.transferCmdReq;
            this.transferCompAck = bit.transferCompAck;
        }
        if (word != null && word.command != null) {
            this.command = GripperCommand.copyFrom(word.command);
        }
    }

    /**
     * 完整複製來源狀態（包含欄位與狀態）
     */
    public void cloneContentFrom(GripperCommandStatus source) {
        if (source == null) return;

        this.gripperId = source.gripperId;
        this.snapshotTime = source.snapshotTime;
        this.transferReady = source.transferReady;
        this.transferCmdReq = source.transferCmdReq;
        this.transferCompAck = source.transferCompAck;

        this.command = GripperCommand.copyFrom(source.command);
        this.complete = source.complete;
        this.available = source.available;
        this.stale = source.stale;

        this.connected = source.connected;
        this.lastConnectedTime = source.lastConnectedTime;
        this.lastDisconnectedTime = source.lastDisconnectedTime;

        this.lastWriteCommand = GripperCommandStatus.copyFrom(source.lastWriteCommand);
    }

    /**
     * 檢查與其他狀態是否不同（只比對有意義的欄位）
     */
    public boolean isContentDifferent(GripperCommandStatus other) {
        if (other == null) return true;

        if (this.transferReady != other.transferReady ||
                this.removeAccountAck != other.removeAccountAck ||
                this.transferCmdReq != other.transferCmdReq ||
                this.transferCompAck != other.transferCompAck) {
            return true;
        }

        if (this.command == null) return other.command != null;
        return !this.command.equals(other.command);
    }

    /**
     * 判斷是否與上次狀態相比有意義的變化
     */
    public boolean hasMeaningfulChange(GripperCommandStatus other) {
        return isContentDifferent(other);
    }

    /**
     * 設定通訊狀態資訊
     */
    public void setConnectedFromPlcStatus(boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        this.connected = plcConnected;
        this.lastConnectedTime = lastConnected;
        this.lastDisconnectedTime = lastDisconnected;
    }

    /**
     * 建立帶有通訊資訊的新狀態
     */
    public static GripperCommandStatus withConnectionInfo(GripperCommandStatus status, boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        status.setConnectedFromPlcStatus(plcConnected, lastConnected, lastDisconnected);
        return status;
    }

    /**
     * 建立完整複製副本
     */
    public static GripperCommandStatus copyFrom(GripperCommandStatus source) {
        if (source == null) return null;
        GripperCommandStatus copy = new GripperCommandStatus();
        copy.cloneContentFrom(source);
        return copy;
    }

    public Integer getTransferNo() {
        return command != null ? command.getTransferNo() : null;
    }
}
