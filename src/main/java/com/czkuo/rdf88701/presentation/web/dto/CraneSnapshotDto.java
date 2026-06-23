package com.czkuo.rdf88701.presentation.web.dto;

import com.czkuo.rdf88701.domain.plc.state.crane.CraneCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneDeviceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CraneSnapshotDto {
    @JsonProperty("craneId") public int craneId;
    @JsonProperty("command") public CraneCommandStatus command;
    @JsonProperty("status")  public CraneDeviceStatus status;

    public CraneSnapshotDto() {}
    public CraneSnapshotDto(int craneId, CraneCommandStatus command, CraneDeviceStatus status) {
        this.craneId = craneId;
        this.command = command;
        this.status = status;
    }
}
