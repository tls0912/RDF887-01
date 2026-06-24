package com.czkuo.rdf88701.common.enums.camera;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public enum CameraErrorCode {
    NONE(0),
    CAMERA_DISCONNECTED(1);

    public final int code;
    CameraErrorCode(int code) { this.code = code; }

    public static CameraErrorCode from(int v) {
        for (var e : values()) if (e.code == v) return e;
        return NONE;
    }
}
