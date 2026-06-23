package com.czkuo.rdf88701.domain.plc.state.workingbeam;

/**
 * WorkingBeamState
 * - 表示 Working Beam 裝置的主狀態（對應 PLC Word 區的 ssss 值）
 */
public enum WorkingBeamState {

    IDLE(1, "Working Beam Idle"),
    PROCESSING(2, "Working Beam Processing"),
    COMPLETE(3, "Working Beam Complete"),
    UNKNOWN(-1, "Unknown");

    private final int code;
    private final String description;

    WorkingBeamState(int code, String description) {
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

    public static WorkingBeamState fromCode(int code) {
        for (WorkingBeamState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
