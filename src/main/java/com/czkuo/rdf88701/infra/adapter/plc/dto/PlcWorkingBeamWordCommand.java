package com.czkuo.rdf88701.infra.adapter.plc.dto;


import lombok.Builder;
import lombok.Data;

/**
 * WorkingBeam → PLC 傳送指令資料封裝 DTO
 * - 對應 Word Memory W0220~W0222
 * - 由上層 Application 組裝後交由 Encoder 編碼
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class PlcWorkingBeamWordCommand {

    /** 任務編號 */
    private int transferNo;

    /** 指令類型（1: Move） */
    private int transferType;

    /** 執行方向（1: IN, 2: OUT） */
    private int direction;
}
