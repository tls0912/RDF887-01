package com.czkuo.rdf88701.infra.adapter.plc.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Crane → PLC 傳送 Command 組裝 DTO
 * - 完全對應 Transfer Job Protocol Word Memory Mapping
 * - 由 Application 填值 → Encoder 封裝為 byte[]
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class PlcCraneWordCommand {

    // ========================
    // From Section
    // ========================
    private int fromCommandType;             // W0050 TTTT (Command Type: 1=Move, 2=From, 8=Fetch)
    private int fromCstType;                 // W0050 cccc (CST Type: 1=Normal)
    private int fromBcrFlag;                 // W0050 b    (BCR Read: 1=Enable, 0=Bypass)
    private int fromTransferNo;              // W0051
    private String fromCstId;                // W0052~W006A (50字 ASCII)
    private int fromLocationType;            // W006B
    private int fromBank;                    // W006C
    private int fromBay;                     // W006D
    private int fromLevel;                   // W006E

    // ========================
    // To Section
    // ========================
    private int toCommandType;               // W006F TTTT (Command Type: 3=To)
    private int toCstType;                   // W006F cccc (CST Type: 1=Normal)
    private int toTransferNo;                // W0070
    private String toCstId;                  // W0071~W0089 (50字 ASCII)
    private int toLocationType;              // W008A
    private int toBank;                      // W008B
    private int toBay;                       // W008C
    private int toLevel;                     // W008D
}
