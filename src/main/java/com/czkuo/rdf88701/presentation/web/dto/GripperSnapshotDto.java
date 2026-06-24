package com.czkuo.rdf88701.presentation.web.dto;

import com.czkuo.rdf88701.domain.plc.state.gripper.GripperCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

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
