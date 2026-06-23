package com.czkuo.rdf88701.domain.plc.state.infrared;

import com.czkuo.rdf88701.domain.plc.valueobject.InfraredStatus;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * InfraredDeviceStatus
 * - 封裝紅外線測高裝置的 PLC 狀態快照（Bit + Word）
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class InfraredDeviceStatus {

    // === Meta 資訊 ===
    private int infraredId;
    private Instant snapshotTime = Instant.now();

    // === Bit 區資料（B07C8~B07CF）===
    private boolean infraredStandby;   // Standby（Ready 狀態，等待新任務）
    private boolean measureCmdAck;     // CMD Ack（PLC 已收到並執行測量命令）
    private boolean measureCompReq;    // Completion Req（PLC 執行完畢，等待 CompAck）
    private boolean alarm;             // 異常旗標

    // === Word 區資料（W1360~W1366）===
    private int productHeight1;        // 測得高度1
    private int productHeight2;        // 測得高度2（如雙感測頭可用）
    private int productQuantity;       // 產品數量
    private InfraredStatus infraredStatus; // 設備狀態/執行狀態（封裝 W1363 RunningStatus/DeviceStatus）
    private int returnCode;            // 指令回傳碼

    // === 補充欄位 ===
    private boolean complete = false;
    private boolean available = true;
    private boolean stale = false;

    private boolean connected;
    private Instant lastConnectedTime;
    private Instant lastDisconnectedTime;

    // =====================================================================================
    // 狀態判斷 Helper Methods
    // =====================================================================================

    /** 判斷快照是否過期 */
    public boolean isOverdue(long thresholdSeconds) {
        return snapshotTime == null || Duration.between(snapshotTime, Instant.now()).getSeconds() > thresholdSeconds;
    }

    /** 是否 Bit/Word 合併完成 */
    public boolean isFullyCombined() {
        return complete;
    }

    /** 綜合檢查有效性 */
    public boolean isValidAndComplete(long thresholdSeconds) {
        return available && !isOverdue(thresholdSeconds) && complete;
    }

    /** 是否處於待命狀態（可接受新指令） */
    public boolean isIdle() {
        return infraredStandby && getRunningStatusCode() == 1 && !measureCmdAck;
    }

    /** 是否 Ready 狀態（可執行新測量） */
    public boolean isReadyToMeasure() {
        return infraredStandby && getDeviceStatusCode() == 1;
    }

    /** 是否已完成測量（PLC 發出 Completion Req） */
    public boolean isMeasurementCompleted() {
        return measureCompReq;
    }

    /** 是否處於異常狀態 */
    public boolean isAbnormal() {
        return alarm;
    }

    /** 是否已發出測量命令（PLC CmdAck Bit） */
    public boolean isMeasureCmdIssued() {
        return measureCmdAck;
    }

    /** 取回傳碼（低16位） */
    public int getReturnCodeValue() {
        return returnCode & 0xFFFF;
    }

    /** 取設備狀態碼（見協定） */
    public int getDeviceStatusCode() {
        return infraredStatus != null ? infraredStatus.getDeviceStatus() : -1;
    }

    /** 取執行狀態碼（見協定） */
    public int getRunningStatusCode() {
        return infraredStatus != null ? infraredStatus.getRunningStatus() : -1;
    }

    /** 主感測器高度（mm），小數點後兩位 */
    public double getProductHeightMm() {
        return productHeight1 / 100.0;
    }

    /** 狀態簡述（可用於 log/debug） */
    public String toSimpleString() {
        return String.format(
                "Infrared#%d - %s, Qty=%d, H=%.2fmm, RetCode=0x%04X, Standby=%s, CmdAck=%s, CompReq=%s",
                infraredId,
                infraredStatus != null ? infraredStatus.getDisplayText() : "Status=N/A",
                productQuantity,
                getProductHeightMm(),
                returnCode,
                infraredStandby, measureCmdAck, measureCompReq
        );
    }

    // =====================================================================================
    // 複製與變更判斷
    // =====================================================================================

    /** 由 bit/word 來源快速 clone */
    public void cloneContentFrom(InfraredDeviceStatus bits, InfraredDeviceStatus words) {
        if (bits == null || words == null) {
            throw new IllegalArgumentException("Both bit and word sources are required.");
        }

        this.infraredStandby = bits.infraredStandby;
        this.measureCmdAck = bits.measureCmdAck;
        this.measureCompReq = bits.measureCompReq;
        this.alarm = bits.alarm;

        this.productQuantity = words.productQuantity;
        this.productHeight1 = words.productHeight1;
        this.productHeight2 = words.productHeight2;
        this.infraredStatus = words.infraredStatus != null ? InfraredStatus.fromWord(words.infraredStatus.toRaw()) : null;
        this.returnCode = words.returnCode;

        this.snapshotTime = Instant.now();
    }

    /** clone 一份（獨立實例） */
    public static InfraredDeviceStatus copyFrom(InfraredDeviceStatus source) {
        if (source == null) return null;
        InfraredDeviceStatus copy = new InfraredDeviceStatus();

        copy.infraredId = source.infraredId;
        copy.snapshotTime = source.snapshotTime;

        copy.infraredStandby = source.infraredStandby;
        copy.measureCmdAck = source.measureCmdAck;
        copy.measureCompReq = source.measureCompReq;
        copy.alarm = source.alarm;

        copy.productQuantity = source.productQuantity;
        copy.productHeight1 = source.productHeight1;
        copy.productHeight2 = source.productHeight2;
        copy.infraredStatus = source.infraredStatus != null ? InfraredStatus.fromWord(source.infraredStatus.toRaw()) : null;
        copy.returnCode = source.returnCode;

        return copy;
    }

    /** 比對內容有無不同（異動檢查） */
    public boolean isContentDifferent(InfraredDeviceStatus other) {
        if (other == null) return true;

        return this.infraredId != other.infraredId ||
                this.infraredStandby != other.infraredStandby ||
                this.measureCmdAck != other.measureCmdAck ||
                this.measureCompReq != other.measureCompReq ||
                this.alarm != other.alarm ||
                this.productQuantity != other.productQuantity ||
                this.productHeight1 != other.productHeight1 ||
                this.productHeight2 != other.productHeight2 ||
                this.returnCode != other.returnCode ||
                (this.infraredStatus == null ? other.infraredStatus != null : !this.infraredStatus.equals(other.infraredStatus));
    }

    // =====================================================================================
    // 連線資訊
    // =====================================================================================

    public void setConnectedFromPlcStatus(boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        this.connected = plcConnected;
        this.lastConnectedTime = lastConnected;
        this.lastDisconnectedTime = lastDisconnected;
    }

    public static InfraredDeviceStatus withConnectionInfo(InfraredDeviceStatus status, boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        status.setConnectedFromPlcStatus(plcConnected, lastConnected, lastDisconnected);
        return status;
    }
}
