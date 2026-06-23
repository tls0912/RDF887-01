package com.czkuo.rdf88701.domain.plc.state.infrared;

/**
 * InfraredState
 * - 表示紅外線設備的主狀態（對應 PLC Word 區的 ssss 值）
 */
public enum InfraredState {

    IDLE(1, "Infrared Idle"),
    WAIT_CMD(2, "Waiting for Command"),
    PROCESSING(3, "Measuring"),
    COMPLETE(4, "Measurement Complete"),
    UNKNOWN(-1, "Unknown");

    private final int code;
    private final String description;

    InfraredState(int code, String description) {
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

    public boolean isWaiting() {
        return this == WAIT_CMD;
    }

    public boolean isProcessing() {
        return this == PROCESSING;
    }

    public boolean isComplete() {
        return this == COMPLETE;
    }

    public static InfraredState fromCode(int code) {
        for (InfraredState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        return UNKNOWN;
    }
}