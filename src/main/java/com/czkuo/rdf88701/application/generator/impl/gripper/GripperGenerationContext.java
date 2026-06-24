package com.czkuo.rdf88701.application.generator.impl.gripper;

import com.czkuo.rdf88701.domain.repository.GripperRequestRepository;
import com.czkuo.rdf88701.domain.repository.GripperTaskRepository;
import com.czkuo.rdf88701.domain.repository.InfraredRequestRepository;
import com.czkuo.rdf88701.domain.repository.InfraredTaskRepository;
import com.czkuo.rdf88701.domain.repository.WorkingBeamRequestRepository;
import com.czkuo.rdf88701.domain.repository.WorkingBeamTaskRepository;

import java.util.HashMap;
import java.util.Map;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

final class GripperGenerationContext {

    private final GripperRequestRepository gripperRequestRepository;
    private final GripperTaskRepository gripperTaskRepository;
    private final InfraredRequestRepository infraredRequestRepository;
    private final InfraredTaskRepository infraredTaskRepository;
    private final WorkingBeamRequestRepository workingBeamRequestRepository;
    private final WorkingBeamTaskRepository workingBeamTaskRepository;

    private final Map<Long, Boolean> gripperBusyById = new HashMap<>();
    private final Map<Long, Boolean> infraredBusyById = new HashMap<>();
    private final Map<Long, Boolean> workingBeamBusyById = new HashMap<>();

    GripperGenerationContext(GripperRequestRepository gripperRequestRepository,
                             GripperTaskRepository gripperTaskRepository,
                             InfraredRequestRepository infraredRequestRepository,
                             InfraredTaskRepository infraredTaskRepository,
                             WorkingBeamRequestRepository workingBeamRequestRepository,
                             WorkingBeamTaskRepository workingBeamTaskRepository) {
        this.gripperRequestRepository = gripperRequestRepository;
        this.gripperTaskRepository = gripperTaskRepository;
        this.infraredRequestRepository = infraredRequestRepository;
        this.infraredTaskRepository = infraredTaskRepository;
        this.workingBeamRequestRepository = workingBeamRequestRepository;
        this.workingBeamTaskRepository = workingBeamTaskRepository;
    }

    boolean gripperBusy(Long gripperId) {
        if (gripperId == null) {
            return false;
        }
        return gripperBusyById.computeIfAbsent(gripperId, id ->
                gripperRequestRepository.existsUnfinishedRequestForDevice(id)
                        || gripperTaskRepository.existsUnfinishedTaskForGripper(id));
    }

    boolean infraredBusy(long infraredId) {
        return infraredBusyById.computeIfAbsent(infraredId, id ->
                infraredRequestRepository.existsUnfinishedRequestForInfrared(id)
                        || infraredTaskRepository.existsUnfinishedTaskForInfrared(id));
    }

    boolean workingBeamBusy(long workingBeamId) {
        if (workingBeamRequestRepository == null || workingBeamTaskRepository == null) {
            return false;
        }
        return workingBeamBusyById.computeIfAbsent(workingBeamId, id ->
                workingBeamRequestRepository.existsUnfinishedRequestForBeam(id)
                        || workingBeamTaskRepository.existsUnfinishedTaskForBeam(id));
    }
}
