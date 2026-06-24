package com.czkuo.rdf88701.common.dto.camera;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public record TwoCamerasSnapshot(
        CameraReadback cam1,
        CameraReadback cam2,
        long tsEpochMillis
) {}
