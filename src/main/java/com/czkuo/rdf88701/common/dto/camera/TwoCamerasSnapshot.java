package com.czkuo.rdf88701.common.dto.camera;

public record TwoCamerasSnapshot(
        CameraReadback cam1,
        CameraReadback cam2,
        long tsEpochMillis
) {}
