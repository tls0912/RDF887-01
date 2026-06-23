package com.czkuo.rdf88701.presentation.web.dto;

import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public class WorkingBeamSnapshotDto {
    @JsonProperty("workingBeamId") public int workingBeamId;
    @JsonProperty("command")       public WorkingBeamCommandStatus command;
    @JsonProperty("status")        public WorkingBeamDeviceStatus status;

    public WorkingBeamSnapshotDto() {}
    public WorkingBeamSnapshotDto(int id, WorkingBeamCommandStatus c, WorkingBeamDeviceStatus s) {
        this.workingBeamId = id; this.command = c; this.status = s;
    }
}
