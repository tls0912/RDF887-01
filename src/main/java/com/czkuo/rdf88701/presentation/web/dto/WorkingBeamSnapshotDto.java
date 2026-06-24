package com.czkuo.rdf88701.presentation.web.dto;

import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public class WorkingBeamSnapshotDto {
    @JsonProperty("workingBeamId") public int workingBeamId;
    @JsonProperty("command")       public WorkingBeamCommandStatus command;
    @JsonProperty("status")        public WorkingBeamDeviceStatus status;

    public WorkingBeamSnapshotDto() {}
    public WorkingBeamSnapshotDto(int id, WorkingBeamCommandStatus c, WorkingBeamDeviceStatus s) {
        this.workingBeamId = id; this.command = c; this.status = s;
    }
}
