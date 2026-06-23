package com.czkuo.rdf88701.domain.plc.state.crane;

/**
 * Crane可辨識的主要流程狀態
 */
public enum CraneState {

    HOME_WAITING(1, "Waiting Home Action"),
    HOMING(2, "Homing"),
    IDLE(3, "Idle"),
    BUSY(4, "Busy"),
    ERROR(5, "Y Axis Not at Home"),
    MAINTAIN(6, "Maintenance"),
    UNKNOWN(-1, "Unknown");

    private final int code;
    private final String description;

    CraneState(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isIdle() {
        return this == IDLE;
    }

    public boolean isBusy() {
        return this == BUSY || this == HOMING || this == HOME_WAITING;
    }

    public boolean isAbnormal() {
        return this == ERROR || this == MAINTAIN;
    }

    public static CraneState fromCode(int code) {
        for (CraneState state : values()) {
            if (state.code == code) return state;
        }
        return UNKNOWN;
    }
}
