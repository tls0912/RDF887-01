package com.czkuo.rdf88701.infra.encoder;

import com.czkuo.rdf88701.domain.plc.state.site.SiteBitSignal;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcSiteBitCommand;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcSiteBitStatus;
import org.springframework.stereotype.Component;

/**
 * PlcSiteBitEncoder
 * - 專責將 Site Bit 指令與狀態進行編碼與解碼
 * - PC → PLC：PlcSiteBitCommand → boolean[]
 * - PLC → PC：boolean[] → PlcSiteBitStatus
 *
 * 位移遵循：
 *  0 = SITE_READY
 *  2 = REMOVE_ACCOUNT_ACK
 *  3 = PORT_REPORT_ACK
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class PlcSiteBitEncoder {

    private static final int BIT_ARRAY_SIZE = 8; // 與 Transfer 一致，保留擴充

    /** 將指令物件編碼為 boolean[]（索引依 SiteBitSignal 定義） */
    public boolean[] encode(PlcSiteBitCommand cmd) {
        boolean[] bits = new boolean[BIT_ARRAY_SIZE];

        bits[SiteBitSignal.SITE_READY.getBitIndex()]         = cmd.isSiteReady();
        bits[SiteBitSignal.REMOVE_ACCOUNT_ACK.getBitIndex()] = cmd.isRemoveAccountAck();
        bits[SiteBitSignal.PORT_REPORT_ACK.getBitIndex()]    = cmd.isPortReportAck();

        return bits;
    }

    /** 將 PLC 傳回的 boolean[] 解碼為狀態物件（索引 0/2/3） */
    public PlcSiteBitStatus decode(boolean[] bits) {
        PlcSiteBitStatus status = new PlcSiteBitStatus();
        status.setSiteReady(getBitSafe(bits, 0));
        status.setRemoveAccountAck(getBitSafe(bits, 2));
        status.setPortReportAck(getBitSafe(bits, 3));
        return status;
    }

    /** 避免越界 */
    private boolean getBitSafe(boolean[] bits, int index) {
        return bits != null && index >= 0 && index < bits.length && bits[index];
    }
}
