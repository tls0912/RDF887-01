package com.czkuo.rdf88701.domain.plc.state.transfer;

import com.czkuo.rdf88701.domain.plc.valueobject.TransferStatus;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * TransferDeviceStatus
 * - 封裝單一 Transfer 裝置的 PLC 回應資料快照
 * - 包含設備位置、狀態碼、回傳碼、產品資訊等
 * - 提供合併、比對、可用判斷與推播資訊
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class TransferDeviceStatus {

    // === Meta 資訊 ===
    private int transferId;
    private Instant snapshotTime = Instant.now();

    // === 來自 Bit 區資料 ===
    private boolean transferStandby;
    private boolean productPresent;
    private boolean removeAccountReq;
    private boolean transferCmdAck;
    private boolean transferCompReq;
    private boolean alarm;

    // === 來自 Word 區資料 ===
    private int bay;
    private int level;
    private int bank;
    private TransferStatus transferStatus;
    private int returnCode;
    private String productId;

    // === 狀態補充欄位（由系統補充）===
    private boolean available = true;
    private boolean complete = false;
    private boolean stale = false;

    // === 通訊狀態 ===
    private boolean connected;
    private Instant lastConnectedTime;
    private Instant lastDisconnectedTime;

    // === 狀態判斷 ===

    public boolean isOverdue(long thresholdSeconds) {
        return snapshotTime == null || Duration.between(snapshotTime, Instant.now()).getSeconds() > thresholdSeconds;
    }

    public boolean isFullyCombined() {
        return complete;
    }

    public boolean isValidAndComplete(long thresholdSeconds) {
        return available && !isOverdue(thresholdSeconds) && isFullyCombined();
    }

    public boolean isIdle() {
        return transferStandby &&
                (transferStatus != null && transferStatus.isIdle()) &&
                !transferCmdAck;
    }

    public boolean isAvailable() {
        return connected && isIdle();
    }

    public boolean isTransferCmdIssued() {
        return transferCmdAck;
    }

    public boolean isTransferCompletedRequest() {
        return transferCompReq;
    }

    public int getReturnCodeValue() {
        return returnCode & 0xFFFF;
    }

    public String toSimpleString() {
        return String.format(
                "Transfer#%d - Status='%s', Bay=%d, Level=%d, Bank=%d, RetCode=0x%04X, Product='%s', Standby=%s, Ack=%s, Req=%s",
                transferId,
                transferStatus != null ? transferStatus.getDisplayText() : null,
                bay, level, bank,
                returnCode,
                productId,
                transferStandby, transferCmdAck, transferCompReq
        );
    }

    // === 狀態複製 ===

    public void cloneContentFrom(TransferDeviceStatus bits, TransferDeviceStatus words) {
        if (bits == null || words == null) {
            throw new IllegalArgumentException("bits and words cannot be null");
        }

        this.transferStandby = bits.transferStandby;
        this.productPresent = bits.productPresent;
        this.removeAccountReq = bits.removeAccountReq;
        this.transferCmdAck = bits.transferCmdAck;
        this.transferCompReq = bits.transferCompReq;
        this.alarm = bits.alarm;

        this.bay = words.bay;
        this.level = words.level;
        this.bank = words.bank;
        this.transferStatus = words.transferStatus;
        this.returnCode = words.returnCode;
        this.productId = words.productId;

        this.snapshotTime = Instant.now();
    }

    public static TransferDeviceStatus copyFrom(TransferDeviceStatus source) {
        if (source == null) return null;
        TransferDeviceStatus copy = new TransferDeviceStatus();

        copy.transferId = source.transferId;
        copy.transferStandby = source.transferStandby;
        copy.productPresent = source.productPresent;
        copy.removeAccountReq = source.removeAccountReq;
        copy.transferCmdAck = source.transferCmdAck;
        copy.transferCompReq = source.transferCompReq;
        copy.alarm = source.alarm;

        copy.bay = source.bay;
        copy.level = source.level;
        copy.bank = source.bank;
        copy.transferStatus = source.transferStatus;
        copy.returnCode = source.returnCode;
        copy.productId = source.productId;

        copy.snapshotTime = source.snapshotTime;
        return copy;
    }

    public boolean isContentDifferent(TransferDeviceStatus other) {
        if (other == null) return true;
        return this.transferId != other.transferId ||
                this.transferStandby != other.transferStandby ||
                this.productPresent != other.productPresent ||
                this.removeAccountReq != other.removeAccountReq ||
                this.transferCmdAck != other.transferCmdAck ||
                this.transferCompReq != other.transferCompReq ||
                this.alarm != other.alarm ||
                this.bay != other.bay ||
                this.level != other.level ||
                this.bank != other.bank ||
                this.returnCode != other.returnCode ||
                !safeEquals(this.transferStatus, other.transferStatus) ||
                !safeEquals(this.productId, other.productId);
    }

    public void setConnectedFromPlcStatus(boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        this.connected = plcConnected;
        this.lastConnectedTime = lastConnected;
        this.lastDisconnectedTime = lastDisconnected;
    }

    public static TransferDeviceStatus withConnectionInfo(TransferDeviceStatus status, boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        status.setConnectedFromPlcStatus(plcConnected, lastConnected, lastDisconnected);
        return status;
    }

    private boolean safeEquals(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}
