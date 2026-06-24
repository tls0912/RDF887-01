package com.czkuo.rdf88701.domain.plc.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * StrappingStatus
 * - 封裝 Strapping 的裝置狀態 Word 資料（W139B）
 * - 對應格式：0000 rrrr ssss ssss
 *   - ssss ssss: Strapping Device Status (DEC)
 *       - 1: Idle
 *       - 2: Wait CMD
 *       - 3: Processing
 *       - 4: Complete
 *   - rrrr: Running Sub Status (DEC)
 *       - 1: IDLE
 *       - 2: STRAPPING
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class StrappingStatus {

    private int raw;
    private int workingStatus;     // ssss ssss
    private int runningSubStatus;  // rrrr

    /**
     * 由 Word 整數解析出對應狀態物件
     */
    public static StrappingStatus fromWord(int word) {
        StrappingStatus result = new StrappingStatus();
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
            case 2 -> "Wait CMD";
            case 3 -> "Processing";
            case 4 -> "Complete";
            default -> "Unknown";
        };
    }

    /**
     * 顯示執行中子狀態文字（中文說明）
     */
    public String getRunningSubStatusText() {
        return switch (runningSubStatus) {
            case 1 -> "IDLE";
            case 2 -> "STRAPPING";
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
     * 是否為忙碌中（處於 Wait CMD 或 Processing 或 STRAPPING 狀態）
     */
    public boolean isBusy() {
        return workingStatus == 2 || workingStatus == 3 || runningSubStatus == 2;
    }

    /**
     * 是否完成（狀態為 Complete）
     */
    public boolean isComplete() {
        return workingStatus == 4;
    }
}
