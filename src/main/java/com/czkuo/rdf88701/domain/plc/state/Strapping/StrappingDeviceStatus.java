package com.czkuo.rdf88701.domain.plc.state.Strapping;

import com.czkuo.rdf88701.domain.plc.valueobject.StrappingStatus;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * StrappingDeviceStatus
 * - 封裝單一 Strapping 的 PLC 回應資料快照
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
public class StrappingDeviceStatus {

    // === Meta 資訊 ===
    private int strappingId;
    private Instant snapshotTime = Instant.now();

    // === 來自 Bit 區資料 ===
    private boolean strappingStandby;    // B0800
    private boolean strappingCmdAck;     // B0803
    private boolean strappingCompReq;    // B0804
    private boolean alarm;               // B0807

    // === 來自 Word 區資料 ===
    private StrappingStatus strappingStatus;  // W139B
    private int returnCode;                   // W139E
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
        return strappingStandby &&
                (strappingStatus != null && strappingStatus.isIdle()) &&
                !strappingCmdAck;
    }

    public boolean isAvailable() {
        return connected && isIdle();
    }

    public boolean isStrappingCmdIssued() {
        return strappingCmdAck;
    }

    public boolean isStrappingCompletedRequest() {
        return strappingCompReq;
    }

    public int getReturnCodeValue() {
        return returnCode & 0xFF;
    }

    public String toSimpleString() {
        return String.format(
                "Strapping#%d - Status='%s', Sub='%s', RetCode=0x%04X, Product='%s', Standby=%s, Ack=%s, Req=%s",
                strappingId,
                strappingStatus != null ? strappingStatus.getWorkingStatus() : null,
                strappingStatus != null ? strappingStatus.getRunningSubStatusText() : null,
                returnCode,
                productId,
                strappingStandby, strappingCmdAck, strappingCompReq
        );
    }

    // === 狀態複製 ===

    public void cloneContentFrom(StrappingDeviceStatus bits, StrappingDeviceStatus words) {
        if (bits == null || words == null) {
            throw new IllegalArgumentException("bits and words cannot be null");
        }

        this.strappingStandby = bits.strappingStandby;
        this.strappingCmdAck = bits.strappingCmdAck;
        this.strappingCompReq = bits.strappingCompReq;
        this.alarm = bits.alarm;

        this.strappingStatus = words.strappingStatus;
        this.returnCode = words.returnCode;
        this.productId = words.productId;
        this.snapshotTime = Instant.now();
    }

    public static StrappingDeviceStatus copyFrom(StrappingDeviceStatus source) {
        if (source == null) return null;
        StrappingDeviceStatus copy = new StrappingDeviceStatus();

        copy.strappingId = source.strappingId;
        copy.strappingStandby = source.strappingStandby;
        copy.strappingCmdAck = source.strappingCmdAck;
        copy.strappingCompReq = source.strappingCompReq;
        copy.alarm = source.alarm;

        copy.strappingStatus = source.strappingStatus;
        copy.returnCode = source.returnCode;
        copy.productId = source.productId;

        copy.snapshotTime = source.snapshotTime;
        return copy;
    }

    public boolean isContentDifferent(StrappingDeviceStatus other) {
        if (other == null) return true;
        return this.strappingId != other.strappingId ||
                this.strappingStandby != other.strappingStandby ||
                this.strappingCmdAck != other.strappingCmdAck ||
                this.strappingCompReq != other.strappingCompReq ||
                this.alarm != other.alarm ||
                this.returnCode != other.returnCode ||
                !safeEquals(this.strappingStatus, other.strappingStatus) ||
                !safeEquals(this.productId, other.productId);
    }

    public void setConnectedFromPlcStatus(boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        this.connected = plcConnected;
        this.lastConnectedTime = lastConnected;
        this.lastDisconnectedTime = lastDisconnected;
    }

    public static StrappingDeviceStatus withConnectionInfo(StrappingDeviceStatus status, boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        status.setConnectedFromPlcStatus(plcConnected, lastConnected, lastDisconnected);
        return status;
    }

    private boolean safeEquals(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}
