package com.czkuo.rdf88701.common.enums.camera;

public enum CameraState {
    IDLE(0),
    FIRST_IN_PROGRESS(1),
    FIRST_DONE_WAIT_SECOND(2),
    SECOND_IN_PROGRESS(3),
    SECOND_DONE_AUTO_TO_IDLE(4), // 會自動回 0
    ERROR(5);

    public final int code;
    CameraState(int code) { this.code = code; }

    public static CameraState from(int v) {
        for (var e : values()) if (e.code == v) return e;
        return ERROR;
    }
}
