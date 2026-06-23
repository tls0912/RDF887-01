package com.czkuo.rdf88701.domain.plc.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * WorkingBeamStatus
 * - 封裝 Working Beam 的裝置狀態 Word 資料（W1223）
 * - 對應格式：0000 rrrr ssss ssss
 *   - ssss ssss: Working Beam Device Status (DEC)
 *       - 1: Idle
 *       - 2: Processing
 *       - 3: Complete
 *   - rrrr: Running Sub Status (DEC)
 *       - 1: IDLE
 *       - 2: MOVING
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class WorkingBeamStatus {

    private int raw;
    private int workingStatus;     // ssss ssss
    private int runningSubStatus;  // rrrr

    /**
     * 由 Word 整數解析出對應狀態物件
     */
    public static WorkingBeamStatus fromWord(int word) {
        WorkingBeamStatus result = new WorkingBeamStatus();
        result.raw = word;
        result.workingStatus = word & 0x00FF;
        result.runningSubStatus = (word >> 8) & 0x000F;
        return result;
    }

    /**
     * 將目前內容組合為原始 Word 整數
     */
    public int toRaw() {
        int value = 0;
        value |= (workingStatus & 0xFF);           // ssss ssss
        value |= (runningSubStatus & 0x0F) << 8;    // rrrr
        return value;
    }

    /**
     * 顯示工作狀態文字（中文說明）
     */
    public String getWorkingStatusText() {
        return switch (workingStatus) {
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
            default -> "UNKNOWN";
        };
    }

    /**
     * 是否為空閒中（工作狀態 + 子狀態皆為 Idle）
     */
    public boolean isIdle() {
        return workingStatus == 1 && runningSubStatus == 1;
    }

    /**
     * 是否為忙碌中（處於 Processing 或 MOVING 狀態）
     */
    public boolean isBusy() {
        return workingStatus == 2 || runningSubStatus == 2;
    }

    /**
     * 是否完成（狀態為 Complete）
     */
    public boolean isComplete() {
        return workingStatus == 3;
    }
}
