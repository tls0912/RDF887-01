package com.czkuo.rdf88701.domain.plc.state.gripper;

import lombok.Getter;

/**
 * GripperState
 * - 表示 Gripper 裝置的主狀態（對應 PLC Word 區的 ssss 值）
 */
public enum GripperState {

    IDLE(1, "Gripper Idle"),
    PROCESSING(2, "Gripper Processing"),
    COMPLETE(3, "Gripper Complete"),
    UNKNOWN(-1, "Unknown");

    private final int code;
    private final String description;

    GripperState(int code, String description) {
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
        return this == PROCESSING;
    }

    public boolean isComplete() {
        return this == COMPLETE;
    }

    public static GripperState fromCode(int code) {
        for (GripperState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
