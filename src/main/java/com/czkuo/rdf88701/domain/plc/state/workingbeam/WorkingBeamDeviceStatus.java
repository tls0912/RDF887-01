package com.czkuo.rdf88701.domain.plc.state.workingbeam;

import com.czkuo.rdf88701.domain.plc.valueobject.WorkingBeamStatus;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * WorkingBeamDeviceStatus
 * - 封裝單一 Working Beam 的 PLC 回應資料快照
 * - 包含執行狀態碼、執行中細部狀態、回應碼、標記等
 * - 提供可比對、合併、推播等邏輯
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class WorkingBeamDeviceStatus {

    // === Meta 資訊 ===
    private int workingBeamId;
    private Instant snapshotTime = Instant.now();

    // === 來自 Bit 區資料 ===
    private boolean transferStandby;
    private boolean transferCmdAck;
    private boolean transferCompReq;
    private boolean alarm;

    // === 來自 Word 區資料 ===
    private WorkingBeamStatus workingBeamStatus;
    private int returnCode;
    private String productId; // optional: 可選擇是否納入

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
                (workingBeamStatus != null && workingBeamStatus.isIdle()) &&
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
        return returnCode & 0xFF;
    }

    public String toSimpleString() {
        return String.format(
                "WorkingBeam#%d - Status='%s', Sub='%s', RetCode=0x%04X, Product='%s', Standby=%s, Ack=%s, Req=%s",
                workingBeamId,
                workingBeamStatus != null ? workingBeamStatus.getWorkingStatus() : null,
                workingBeamStatus != null ? workingBeamStatus.getRunningSubStatusText() : null,
                returnCode,
                productId,
                transferStandby, transferCmdAck, transferCompReq
        );
    }

    // === 狀態複製 ===

    public void cloneContentFrom(WorkingBeamDeviceStatus bits, WorkingBeamDeviceStatus words) {
        if (bits == null || words == null) {
            throw new IllegalArgumentException("bits and words cannot be null");
        }

        this.transferStandby = bits.transferStandby;
        this.transferCmdAck = bits.transferCmdAck;
        this.transferCompReq = bits.transferCompReq;
        this.alarm = bits.alarm;

        this.workingBeamStatus = words.workingBeamStatus;
        this.returnCode = words.returnCode;
        this.productId = words.productId;
        this.snapshotTime = Instant.now();
    }

    public static WorkingBeamDeviceStatus copyFrom(WorkingBeamDeviceStatus source) {
        if (source == null) return null;
        WorkingBeamDeviceStatus copy = new WorkingBeamDeviceStatus();

        copy.workingBeamId = source.workingBeamId;
        copy.transferStandby = source.transferStandby;
        copy.transferCmdAck = source.transferCmdAck;
        copy.transferCompReq = source.transferCompReq;
        copy.alarm = source.alarm;

        copy.workingBeamStatus = source.workingBeamStatus;
        copy.returnCode = source.returnCode;
        copy.productId = source.productId;

        copy.snapshotTime = source.snapshotTime;
        return copy;
    }

    public boolean isContentDifferent(WorkingBeamDeviceStatus other) {
        if (other == null) return true;
        return this.workingBeamId != other.workingBeamId ||
                this.transferStandby != other.transferStandby ||
                this.transferCmdAck != other.transferCmdAck ||
                this.transferCompReq != other.transferCompReq ||
                this.alarm != other.alarm ||
                this.returnCode != other.returnCode ||
                !safeEquals(this.workingBeamStatus, other.workingBeamStatus) ||
                !safeEquals(this.productId, other.productId);
    }

    public void setConnectedFromPlcStatus(boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        this.connected = plcConnected;
        this.lastConnectedTime = lastConnected;
        this.lastDisconnectedTime = lastDisconnected;
    }

    public static WorkingBeamDeviceStatus withConnectionInfo(WorkingBeamDeviceStatus status, boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        status.setConnectedFromPlcStatus(plcConnected, lastConnected, lastDisconnected);
        return status;
    }

    private boolean safeEquals(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}
