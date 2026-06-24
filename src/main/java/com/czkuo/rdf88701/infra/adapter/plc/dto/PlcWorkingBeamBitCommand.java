package com.czkuo.rdf88701.infra.adapter.plc.dto;

import lombok.Data;

/**
 * Plc WorkingBeam Bit Command DTO
 * - Application 層傳遞給 Encoder 使用的交握控制結構
 * - 僅封裝有效控制位元
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class PlcWorkingBeamBitCommand {

    /** Working Beam Ready（B0148） */
    private boolean workingBeamReady;

    /** 啟動指令請求（B014D） */
    private boolean transferCmdReq;

    /** 指令完成回應（B014E） */
    private boolean transferCompAck;
}
