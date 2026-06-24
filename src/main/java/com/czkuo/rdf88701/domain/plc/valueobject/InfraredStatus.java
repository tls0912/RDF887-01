package com.czkuo.rdf88701.domain.plc.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * InfraredStatus
 * - 封裝紅外線設備的 Word 狀態（對應 W1363）
 * - 對應格式：0000 rrrr ssss ssss
 *     - ssss: Device Status (1: Idle, 2: Wait CMD, 3: Processing, 4: Complete)
 *     - rrrr: Running Status (1: IDLE, 2: MEASURE)
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class InfraredStatus {

    private int raw;                  // 原始 Word 值
    private int deviceStatus;        // ssss
    private int runningStatus;       // rrrr

    public static InfraredStatus fromWord(int word) {
        InfraredStatus status = new InfraredStatus();
        status.raw = word;
        status.deviceStatus = word & 0x00FF;          // 最右 8 bit: ssss
        status.runningStatus = (word >> 8) & 0x0F;    // bits 8~11: rrrr
        return status;
    }

    public int toRaw() {
        int word = 0;
        word |= (deviceStatus & 0xFF);             // bits 0~7
        word |= (runningStatus & 0x0F) << 8;       // bits 8~11
        return word;
    }

    public boolean isIdle() {
        return deviceStatus == 1 && runningStatus == 1;
    }

    public boolean isMeasuring() {
        return runningStatus == 2;
    }

    public boolean isComplete() {
        return deviceStatus == 4;
    }

    public String getWorkingStatusText() {
        return switch (deviceStatus) {
            case 1 -> "Idle";
            case 2 -> "Wait CMD";
            case 3 -> "Processing";
            case 4 -> "Complete";
            default -> "Unknown";
        };
    }

    public String getRunningStatusText() {
        return switch (runningStatus) {
            case 1 -> "IDLE";
            case 2 -> "MEASURE";
            default -> "UNKNOWN";
        };
    }

    public String getDisplayText() {
        return getWorkingStatusText() + "/" + getRunningStatusText();
    }
}
