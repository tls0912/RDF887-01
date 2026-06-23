package com.czkuo.rdf88701.infra.adapter.plc.dto;

import lombok.Data;

/**
 * Plc Transfer Bit Command DTO
 * - Application 層傳遞給 Encoder 使用的交握控制結構
 * - 僅封裝有效控制位元
 */
@Data
public class PlcTransferBitCommand {

    /** Transfer Ready（B0108） */
    private boolean transferReady;

    /** 啟動指令請求（B010D） */
    private boolean transferCmdReq;

    /** 指令完成回應（B010E） */
    private boolean transferCompAck;
}
