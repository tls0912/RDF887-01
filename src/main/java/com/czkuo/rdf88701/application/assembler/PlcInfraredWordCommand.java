package com.czkuo.rdf88701.application.assembler;

import lombok.Builder;
import lombok.Data;

/**
 * Infrared → PLC 傳送指令資料封裝 DTO
 * - 對應 Word Memory（如 W0360~W0362）
 * - 由上層 Application 組裝後交由 Encoder 編碼
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class PlcInfraredWordCommand {

    /** 任務編號 */
    private int measureNo;

    /** 任務型態（1: MEASURE） */
    private int taskType;

    /** Tray 盤厚度 */
    private int trayThickness;

}
