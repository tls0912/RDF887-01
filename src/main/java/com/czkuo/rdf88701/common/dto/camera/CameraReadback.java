package com.czkuo.rdf88701.common.dto.camera;

import com.czkuo.rdf88701.common.enums.camera.CameraErrorCode;
import com.czkuo.rdf88701.common.enums.camera.CameraState;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public record CameraReadback(
        CameraState state,
        CameraErrorCode error,
        int firstCount,
        int secondCount,
        int total,
        int times
) {}
