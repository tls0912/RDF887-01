package com.czkuo.rdf88701.domain.plc.state.crane;

import lombok.*;

import java.time.Duration;
import java.time.Instant;

/**
 * CraneDeviceStatus
 * - 封裝一個完整 Crane 當前 PLC 資料快照
 * - 包含位置資訊、狀態旗標、執行狀態、回應碼、產品資訊等
 * - 提供狀態判斷、合併工具、比對邏輯
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class CraneDeviceStatus {

    // === Meta 資訊 ===
    private int craneId;
    private Instant snapshotTime = Instant.now();

    // === 來自 Bit 區資料 ===
    private boolean transferStandby;
    private boolean cstPresent;
    private boolean readyHandleFromCmd;
    private boolean readyHandleToCmd;
    private boolean fromJobHandling;
    private boolean fromTransferCmdAck;
    private boolean fromTransferCompReq;
    private boolean toJobHandling;
    private boolean toTransferCmdAck;
    private boolean toTransferCompReq;
    private boolean homeReturnAck;
    private boolean removeAccountReq;

    // === 來自 Word 區資料 ===
    private int bayPosition;
    private int levelPosition;
    private int bankPosition;
    private int deviceStatus; // PLC Word W1083
    private int productHeight;
    private int fromReturnCode;
    private int toReturnCode;
    private String productId;

    // === 狀態補充欄位（由系統補充）===
    private boolean available = true;
    private boolean complete = false;
    private boolean stale = false;

    // === 通訊狀態 ===
    private boolean connected;
    private Instant lastConnectedTime;
    private Instant lastDisconnectedTime;

    // ============================================================
    // CraneState 推導方法
    // ============================================================

    public CraneState getCurrentCraneState() {
        return CraneState.fromCode(this.deviceStatus);
    }

    public boolean isTransferDeviceIdle() {
        return getCurrentCraneState().isIdle();
    }

    public String getTransferDeviceStatusDesc() {
        return getCurrentCraneState().getDescription();
    }

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

    public boolean isIdle() {
        return transferStandby &&
                !fromJobHandling &&
                !toJobHandling &&
                !fromTransferCmdAck &&
                !toTransferCmdAck;
    }

    public boolean isAvailable() {
        return connected && isIdle();
    }

    public String toSimpleString() {
        return String.format(
                "Crane#%d - Bay:%d, Level:%d, Bank:%d, Status=0x%04X, FromRet=0x%04X, ToRet=0x%04X, Product='%s', CSTPresent=%s",
                craneId, bayPosition, levelPosition, bankPosition,
                deviceStatus, fromReturnCode, toReturnCode, productId,
                cstPresent ? "Y" : "N"
        );
    }

    public void cloneContentFrom(CraneDeviceStatus bits, CraneDeviceStatus words) {
        if (bits == null || words == null) {
            throw new IllegalArgumentException("bits and words cannot be null");
        }

        this.transferStandby = bits.transferStandby;
        this.cstPresent = bits.cstPresent;
        this.readyHandleFromCmd = bits.readyHandleFromCmd;
        this.readyHandleToCmd = bits.readyHandleToCmd;
        this.fromJobHandling = bits.fromJobHandling;
        this.fromTransferCmdAck = bits.fromTransferCmdAck;
        this.fromTransferCompReq = bits.fromTransferCompReq;
        this.toJobHandling = bits.toJobHandling;
        this.toTransferCmdAck = bits.toTransferCmdAck;
        this.toTransferCompReq = bits.toTransferCompReq;
        this.homeReturnAck = bits.homeReturnAck;
        this.removeAccountReq = bits.removeAccountReq;

        this.bayPosition = words.bayPosition;
        this.levelPosition = words.levelPosition;
        this.bankPosition = words.bankPosition;
        this.deviceStatus = words.deviceStatus;
        this.productHeight = words.productHeight;
        this.fromReturnCode = words.fromReturnCode;
        this.toReturnCode = words.toReturnCode;
        this.productId = words.productId;
    }

    public void setConnectedFromPlcStatus(boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        this.connected = plcConnected;
        this.lastConnectedTime = lastConnected;
        this.lastDisconnectedTime = lastDisconnected;
    }

    public static CraneDeviceStatus withConnectionInfo(CraneDeviceStatus status, boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        status.setConnectedFromPlcStatus(plcConnected, lastConnected, lastDisconnected);
        return status;
    }

    public boolean isContentDifferent(CraneDeviceStatus other) {
        if (other == null) return true;
        return this.transferStandby != other.transferStandby ||
                this.cstPresent != other.cstPresent ||
                this.readyHandleFromCmd != other.readyHandleFromCmd ||
                this.readyHandleToCmd != other.readyHandleToCmd ||
                this.fromJobHandling != other.fromJobHandling ||
                this.fromTransferCmdAck != other.fromTransferCmdAck ||
                this.fromTransferCompReq != other.fromTransferCompReq ||
                this.toJobHandling != other.toJobHandling ||
                this.toTransferCmdAck != other.toTransferCmdAck ||
                this.toTransferCompReq != other.toTransferCompReq ||
                this.homeReturnAck != other.homeReturnAck ||
                this.removeAccountReq != other.removeAccountReq ||
                this.bayPosition != other.bayPosition ||
                this.levelPosition != other.levelPosition ||
                this.bankPosition != other.bankPosition ||
                this.deviceStatus != other.deviceStatus ||
                this.fromReturnCode != other.fromReturnCode ||
                this.toReturnCode != other.toReturnCode ||
                !safeEquals(this.productId, other.productId);
    }

    public static CraneDeviceStatus copyFrom(CraneDeviceStatus source) {
        if (source == null) return null;
        CraneDeviceStatus copy = new CraneDeviceStatus();

        copy.transferStandby = source.transferStandby;
        copy.cstPresent = source.cstPresent;
        copy.readyHandleFromCmd = source.readyHandleFromCmd;
        copy.readyHandleToCmd = source.readyHandleToCmd;
        copy.fromJobHandling = source.fromJobHandling;
        copy.fromTransferCmdAck = source.fromTransferCmdAck;
        copy.fromTransferCompReq = source.fromTransferCompReq;
        copy.toJobHandling = source.toJobHandling;
        copy.toTransferCmdAck = source.toTransferCmdAck;
        copy.toTransferCompReq = source.toTransferCompReq;
        copy.homeReturnAck = source.homeReturnAck;
        copy.removeAccountReq = source.removeAccountReq;

        copy.bayPosition = source.bayPosition;
        copy.levelPosition = source.levelPosition;
        copy.bankPosition = source.bankPosition;
        copy.deviceStatus = source.deviceStatus;
        copy.fromReturnCode = source.fromReturnCode;
        copy.toReturnCode = source.toReturnCode;
        copy.productId = source.productId;

        return copy;
    }

    private boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    // === 供 StepResolver 使用的標準旗標名稱 ===
    public boolean isFromTransferCmdIssued() {
        return fromTransferCmdAck;
    }

    public boolean isToTransferCmdIssued() {
        return toTransferCmdAck;
    }

    public boolean isTransferCompletedRequest() {
        return fromTransferCompReq || toTransferCompReq;
    }

    public int getFromReturnCodeValue() {
        return (fromReturnCode >> 8) & 0xFF;  // 高位
    }

    public int getToReturnCodeValue() {
        return toReturnCode & 0xFF;          // 低位
    }
}