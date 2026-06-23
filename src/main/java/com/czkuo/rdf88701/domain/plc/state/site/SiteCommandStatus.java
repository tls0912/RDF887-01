package com.czkuo.rdf88701.domain.plc.state.site;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * SiteCommandDeviceStatus
 * - 封裝 PC → PLC 傳送的 Site#1 控制區（Bit + Word 區資訊）
 * - 包含控制位元、50字元 ASCII ID 資料、通訊狀態與快照
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class SiteCommandStatus {

    // === Meta 資訊 ===
    private int siteId;
    private Instant snapshotTime = Instant.now(); // 資料取得時間

    // === Bit 區資料（B0248 ~ B024B）===
    private boolean siteReady;             // B0248: Bit 0
    private boolean removeAccountAck;      // B024A: Bit 2
    private boolean portReportPc;          // B024B: Bit 3

    // === Word 區資料（W03E6 ~ W03FE）===
    private String productId;         // 共 50 字元 ASCII 資料

    // === 狀態補充欄位 ===
    private boolean complete = false;
    private boolean available = true;
    private boolean stale = false;

    // === 通訊狀態欄位 ===
    private boolean connected;
    private Instant lastConnectedTime;
    private Instant lastDisconnectedTime;

    // === 快照（上次寫入內容）===
    private SiteCommandStatus lastWriteCommand;

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
                "Site#%d - Ready=%s, Ack(Remove)=%s, Ack(ReportIn)=%s, ID='%s'",
                siteId,
                siteReady ? "Y" : "N",
                removeAccountAck ? "Y" : "N",
                portReportPc ? "Y" : "N",
                productId != null ? productId.trim() : ""
        );
    }

    // =====================================================================================
    // 複製與比對邏輯
    // =====================================================================================

    public void cloneContentFrom(SiteCommandStatus bit, SiteCommandStatus word) {
        if (bit != null) {
            this.siteReady = bit.siteReady;
            this.removeAccountAck = bit.removeAccountAck;
            this.portReportPc = bit.portReportPc;
        }
        if (word != null) {
            this.productId = word.productId;
        }
    }

    public void cloneContentFrom(SiteCommandStatus source) {
        if (source == null) return;

        this.siteId = source.siteId;
        this.snapshotTime = source.snapshotTime;

        this.siteReady = source.siteReady;
        this.removeAccountAck = source.removeAccountAck;
        this.portReportPc = source.portReportPc;

        this.productId = source.productId;

        this.complete = source.complete;
        this.available = source.available;
        this.stale = source.stale;

        this.connected = source.connected;
        this.lastConnectedTime = source.lastConnectedTime;
        this.lastDisconnectedTime = source.lastDisconnectedTime;

        this.lastWriteCommand = copyFrom(source.lastWriteCommand);
    }

    public boolean isContentDifferent(SiteCommandStatus other) {
        if (other == null) return true;

        return this.siteReady != other.siteReady
                || this.removeAccountAck != other.removeAccountAck
                || this.portReportPc != other.portReportPc
                || !Objects.equals(this.productId, other.productId);
    }

    public boolean hasMeaningfulChange(SiteCommandStatus other) {
        return isContentDifferent(other);
    }

    public void setConnectedFromPlcStatus(boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        this.connected = plcConnected;
        this.lastConnectedTime = lastConnected;
        this.lastDisconnectedTime = lastDisconnected;
    }

    public static SiteCommandStatus withConnectionInfo(SiteCommandStatus status, boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        status.setConnectedFromPlcStatus(plcConnected, lastConnected, lastDisconnected);
        return status;
    }

    public static SiteCommandStatus copyFrom(SiteCommandStatus source) {
        if (source == null) return null;
        SiteCommandStatus copy = new SiteCommandStatus();
        copy.cloneContentFrom(source);
        return copy;
    }
}
