package com.czkuo.rdf88701.infra.adapter.plc.dto;

import lombok.Data;

/**
 * Crane PLC Bit Status
 * - PLC 回傳 PC 狀態專用
 */
@Data
public class PlcCraneBitStatus {

    // Transfer Job Handshake
    private boolean fromJobHandling;          // B0640
    private boolean fromTransferCmdAck;       // B0641
    private boolean fromTransferCompReq;      // B0642
    private boolean toJobHandling;            // B0643
    private boolean toTransferCmdAck;         // B0644
    private boolean toTransferCompReq;        // B0645

    // Home Search
    private boolean homeReturnAck;            // B064A
    private boolean removeAccountReq;         // B064B

    // 其餘 Spare 不建議放進來
}
