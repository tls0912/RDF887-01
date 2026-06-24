package com.czkuo.rdf88701.domain.plc.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * TransferStatus
 * - 封裝 Transfer 裝置的狀態 Word 資料（W1103）
 * - 對應格式：wxyz rrrr ssss ssss
 *   - ssss ssss: Transfer Device Status (DEC)
 *       - 1: Idle
 *       - 2: Processing
 *       - 3: Complete
 *   - rrrr: Running Sub Status (DEC)
 *       - 1: IDLE
 *       - 2: MOVING
 *       - 3: PICKING
 *       - 4: DROPPING
 *   - w: Crane Available
 *   - x: Transfer Available
 *   - y: Gripper Available
 *   - z: Working Beam Available
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class TransferStatus {

    private int raw;                    // 原始 Word 值
    private int transferStatus;         // ssss ssss
    private int runningSubStatus;       // rrrr
    private boolean craneAvailable;     // w
    private boolean transferAvailable;  // x
    private boolean gripperAvailable;   // y
    private boolean workingBeamAvailable; // z

    /**
     * 由 Word 值解析出 TransferStatus 物件
     */
    public static TransferStatus fromWord(int word) {
        TransferStatus status = new TransferStatus();
        status.raw = word;
        status.transferStatus = word & 0x00FF;                      // 最右 8 bit
        status.runningSubStatus = (word >> 8) & 0x000F;             // bits 8~11
        status.workingBeamAvailable = ((word >> 12) & 0x01) == 1;   // bit 12 (z)
        status.gripperAvailable = ((word >> 13) & 0x01) == 1;       // bit 13 (y)
        status.transferAvailable = ((word >> 14) & 0x01) == 1;      // bit 14 (x)
        status.craneAvailable = ((word >> 15) & 0x01) == 1;         // bit 15 (w)
        return status;
    }

    /**
     * 將目前內容組成 Word 整數
     */
    public int toRaw() {
        int word = 0;
        word |= (transferStatus & 0xFF);                // bits 0~7
        word |= (runningSubStatus & 0x0F) << 8;         // bits 8~11
        word |= (workingBeamAvailable ? 1 : 0) << 12;   // bit 12
        word |= (gripperAvailable ? 1 : 0) << 13;       // bit 13
        word |= (transferAvailable ? 1 : 0) << 14;      // bit 14
        word |= (craneAvailable ? 1 : 0) << 15;         // bit 15
        return word;
    }

    /**
     * 顯示工作狀態文字（中文說明）
     */
    public String getWorkingStatusText() {
        return switch (transferStatus) {
            case 1 -> "Idle";
            case 2 -> "Processing";
            case 3 -> "Complete";
            default -> "Unknown";
        };
    }

    /**
     * 顯示執行中子狀態文字（中文說明）
     */
    public String getRunningSubStatusText() {
        return switch (runningSubStatus) {
            case 1 -> "IDLE";
            case 2 -> "MOVING";
            case 3 -> "PICKING";
            case 4 -> "DROPPING";
            default -> "UNKNOWN";
        };
    }

    public String getDisplayText() {
        return getWorkingStatusText() + "/" + getRunningSubStatusText();
    }

    public boolean isIdle() {
        return transferStatus == 1 && runningSubStatus == 1;
    }

    public boolean isBusy() {
        return transferStatus == 2 || runningSubStatus == 2 || runningSubStatus == 3 || runningSubStatus == 4;
    }

    public boolean isComplete() {
        return transferStatus == 3;
    }

    public boolean isAnyResourceAvailable() {
        return craneAvailable || transferAvailable || gripperAvailable || workingBeamAvailable;
    }
}
