package com.czkuo.rdf88701.infra.adapter.plc.dto;

import lombok.Data;

/**
 * Plc Site Bit Command DTO
 * - Application 層傳遞給 Encoder / Writer 的交握控制結構
 * - 僅封裝有效控制位元（PC → PLC, Write-B 區）
 *   base + 0 = Site Ready
 *   base + 2 = Remove Account Ack
 *   base + 3 = Port Report Ack（若站無此位可不使用）
 */
@Data
public class PlcSiteBitCommand {

    /** Site Ready（base + 0） */
    private boolean siteReady;

    /** Remove Account Ack（base + 2） */
    private boolean removeAccountAck;

    /** Port Report Ack（base + 3） */
    private boolean portReportAck;
}
