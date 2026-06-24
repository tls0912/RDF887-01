package com.czkuo.rdf88701.domain.plc.state.site;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * SiteDeviceStatus
 * - 封裝單一 Site 裝置的 PLC 回應資料快照
 * - 包含現場狀態旗標（Bit 區）、裝置狀態碼、產品資訊（Word 區）等
 * - 提供合併、狀態比對、推播用資料格式
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class SiteDeviceStatus {

    // === Meta 資訊 ===
    private int siteId;
    private Instant snapshotTime = Instant.now();

    // === 來自 Bit 區資料 ===
    private boolean siteStandby;         // index 0 - Site Station is ready
    private boolean productPresent;      // index 1 - 有產品
    private boolean removeAccountReq;    // index 2 - 請求刪帳
    private boolean portReportPlc;       // index 3 - IN/OUT 由上層判斷語意

    // === 來自 Word 區資料 ===
    private int deviceStatus;            // wxyz：各設備可用
    private String productId;            // 50 字元 ASCII 組成的 Product ID

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
        return siteStandby && !portReportPlc;
    }

    public boolean isAvailable() {
        return connected && isIdle();
    }

    public String toSimpleString() {
        return String.format(
                "Site#%d - Standby=%s, Present=%s, RemoveReq=%s, PortReq=%s, DeviceStatus=0x%04X, Product='%s'",
                siteId,
                siteStandby, productPresent, removeAccountReq, portReportPlc,
                deviceStatus,
                productId
        );
    }

    // === 狀態複製 ===

    public void cloneContentFrom(SiteDeviceStatus bits, SiteDeviceStatus words) {
        if (bits == null || words == null) {
            throw new IllegalArgumentException("bits and words cannot be null");
        }

        this.siteStandby = bits.siteStandby;
        this.productPresent = bits.productPresent;
        this.removeAccountReq = bits.removeAccountReq;
        this.portReportPlc = bits.portReportPlc;

        this.deviceStatus = words.deviceStatus;
        this.productId = words.productId;

        this.snapshotTime = Instant.now();
    }

    public static SiteDeviceStatus copyFrom(SiteDeviceStatus source) {
        if (source == null) return null;
        SiteDeviceStatus copy = new SiteDeviceStatus();

        copy.siteId = source.siteId;
        copy.siteStandby = source.siteStandby;
        copy.productPresent = source.productPresent;
        copy.removeAccountReq = source.removeAccountReq;
        copy.portReportPlc = source.portReportPlc;

        copy.deviceStatus = source.deviceStatus;
        copy.productId = source.productId;

        copy.snapshotTime = source.snapshotTime;
        return copy;
    }

    public boolean isContentDifferent(SiteDeviceStatus other) {
        if (other == null) return true;
        return this.siteId != other.siteId ||
                this.siteStandby != other.siteStandby ||
                this.productPresent != other.productPresent ||
                this.removeAccountReq != other.removeAccountReq ||
                this.portReportPlc != other.portReportPlc ||
                this.deviceStatus != other.deviceStatus ||
                !safeEquals(this.productId, other.productId);
    }

    public void setConnectedFromPlcStatus(boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        this.connected = plcConnected;
        this.lastConnectedTime = lastConnected;
        this.lastDisconnectedTime = lastDisconnected;
    }

    public static SiteDeviceStatus withConnectionInfo(SiteDeviceStatus status, boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        status.setConnectedFromPlcStatus(plcConnected, lastConnected, lastDisconnected);
        return status;
    }

    private boolean safeEquals(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}
