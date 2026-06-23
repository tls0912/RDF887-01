package com.czkuo.rdf88701.infra.adapter.plc.dto;

import lombok.Data;

/**
 * Plc Crane Bit Command DTO
 * - Application 層傳遞給 Encoder 用的資料結構
 * - 只包含有效的交握訊號
 */
@Data
public class PlcCraneBitCommand {

    // Transfer Job Handshake
    private boolean transferReady;                 // B0030
    private boolean fromTransferCmdReq;            // B0031
    private boolean fromTransferCompAck;           // B0032
    private boolean toTransferCmdReq;              // B0033
    private boolean toTransferCompAck;             // B0034

    // Home Return Handshake
    private boolean homeReturnRequest;             // B0037
    private boolean removeAccountAck;              // B0038

    // 目前 Spare 全部省略
}
