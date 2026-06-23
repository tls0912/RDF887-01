package com.czkuo.rdf88701.infra.adapter.plc.dto;

import lombok.Data;

/**
 * Plc Site Bit Status DTO
 * - 將 PLC 傳回的 Site 位元狀態解碼後承載於此
 * - 對應位移：
 *   0 = Site Ready
 *   2 = Remove Account Ack
 *   3 = Port Report Ack
 */
@Data
public class PlcSiteBitStatus {

    /** base + 0 */
    private boolean siteReady;

    /** base + 2 */
    private boolean removeAccountAck;

    /** base + 3 */
    private boolean portReportAck;
}
