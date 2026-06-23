package com.czkuo.rdf88701.common.dto.camera;

import com.czkuo.rdf88701.common.enums.camera.CameraErrorCode;
import com.czkuo.rdf88701.common.enums.camera.CameraState;

public record CameraReadback(
        CameraState state,
        CameraErrorCode error,
        int firstCount,
        int secondCount,
        int total,
        int times
) {}
