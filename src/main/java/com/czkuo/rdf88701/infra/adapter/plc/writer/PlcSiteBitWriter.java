package com.czkuo.rdf88701.infra.adapter.plc.writer;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.PlcSiteRegistry;
import com.czkuo.rdf88701.domain.plc.state.site.SiteBitSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PlcSiteBitWriter
 * ------------------------------------------------------------
 * - 專責寫入「Site（PC→PLC）」的 Write-Bit 區段
 * - 位址計算：final = writeBitBase(siteId) + offset
 *   例：Site#2 base = B024C → offset 0 = B024C, 2 = B024E
 *
 * 依賴：
 *  - PlcAccessService：實際對 PLC 寫入
 *  - PlcSiteRegistry：解析裝置代碼、Write-B 起始位址
 *
 * 使用範例：
 *  plcSiteBitWriter.writeSiteReady(2, true);             // B024C = 1
 *  plcSiteBitWriter.writeRemoveAccountAck(2, true);      // B024E = 1
 *  plcSiteBitWriter.writeBit(2, 0, false);               // 直接用 offset
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcSiteBitWriter {

    private final PlcAccessService plcAccessService;
    private final PlcSiteRegistry plcSiteRegistry;

    /**
     * 以 offset 指定要寫的 bit。
     *
     * @param siteId Site ID（例：2）
     * @param offset 相對於該 Site 的 Write-B 起始位址的位移（0..7）
     * @param value  true/false
     */
    public void writeBit(int siteId, int offset, boolean value) {
        if (offset < 0 || offset > 15) { // 保留擴充，常見 0..7
            throw new IllegalArgumentException("Invalid bit offset: " + offset);
        }
        String siteName  = plcSiteRegistry.getSiteNameById(siteId);
        String device    = plcSiteRegistry.resolvePlcDeviceNameById(siteId);
        int baseAddress  = plcSiteRegistry.getHandshakeBitStartAddress(siteName);
        int finalAddress = baseAddress + offset;
        String address   = "B" + PlcAddressUtils.formatAddressHexWithout0x(finalAddress);

        plcAccessService.writeBoolean(device, address, value);
        //log.debug("[PLC] [{}] Write bit: {} -> {}", siteName, address, value);
    }

    /**
     * 使用 enum 寫具名 bit（建議用這個，語義清楚）
     */
    public void writeBit(int siteId, SiteBitSignal signal, boolean value) {
        writeBit(siteId, signal.getBitIndex(), value);
    }

    // ===========================
    // 語義化常用操作
    // ===========================

    /** Site Ready (Enable/Disable) */
    public void writeSiteReady(int siteId, boolean value) {
        writeBit(siteId, SiteBitSignal.SITE_READY, value);
    }

    /** Remove Account Ack */
    public void writeRemoveAccountAck(int siteId, boolean value) {
        writeBit(siteId, SiteBitSignal.REMOVE_ACCOUNT_ACK, value);
    }

    /** 若站上有 Port Report Ack，可開啟 SiteBitSignal.PORT_REPORT_ACK 後使用 */
    public void writePortReportAck(int siteId, boolean value) {
        writeBit(siteId, SiteBitSignal.PORT_REPORT_ACK, value);
    }
}
