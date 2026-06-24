package com.czkuo.rdf88701.domain.plc.state.Strapping;

/**
 * StrappingState
 * - 表示 Strapping 裝置的主狀態（對應 PLC Word 區的 ssss 值）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public enum StrappingState {

    IDLE(1, "Strapping Idle"),
    PROCESSING(2, "Strapping Processing"),
    COMPLETE(3, "Strapping Complete"),
    UNKNOWN(-1, "Unknown");

    private final int code;
    private final String description;

    StrappingState(int code, String description) {
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

    public static StrappingState fromCode(int code) {
        for (StrappingState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
