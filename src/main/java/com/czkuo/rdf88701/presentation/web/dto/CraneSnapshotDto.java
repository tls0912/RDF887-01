package com.czkuo.rdf88701.presentation.web.dto;

import com.czkuo.rdf88701.domain.plc.state.crane.CraneCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneDeviceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

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
