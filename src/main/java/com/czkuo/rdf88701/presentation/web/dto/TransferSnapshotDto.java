package com.czkuo.rdf88701.presentation.web.dto;

import com.czkuo.rdf88701.domain.plc.state.transfer.TransferCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

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
