package com.czkuo.rdf88701.presentation.web.dto;

import com.czkuo.rdf88701.domain.plc.state.transfer.TransferCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TransferSnapshotDto {
    @JsonProperty("transferId") public int transferId;
    @JsonProperty("command")    public TransferCommandStatus command;
    @JsonProperty("status")     public TransferDeviceStatus status;

    public TransferSnapshotDto() { }
    public TransferSnapshotDto(int transferId, TransferCommandStatus command, TransferDeviceStatus status) {
        this.transferId = transferId;
        this.command = command;
        this.status = status;
    }
}
