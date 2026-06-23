package com.czkuo.rdf88701.presentation.web.dto;

import com.czkuo.rdf88701.domain.plc.state.gripper.GripperCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GripperSnapshotDto {
    @JsonProperty("gripperId") public int gripperId;
    @JsonProperty("command")   public GripperCommandStatus command;
    @JsonProperty("status")    public GripperDeviceStatus status;

    public GripperSnapshotDto() {}
    public GripperSnapshotDto(int gripperId, GripperCommandStatus command, GripperDeviceStatus status) {
        this.gripperId = gripperId;
        this.command = command;
        this.status = status;
    }
}
