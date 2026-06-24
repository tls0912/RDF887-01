package com.czkuo.rdf88701.infra.adapter.plc.writer;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.PlcSiteRegistry;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcSiteWordCommand;
import com.czkuo.rdf88701.infra.encoder.PlcSiteWordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PlcSiteWordWriter
 * - 專責將 Site 指令的 Word 區（含 ASCII50）寫入 PLC
 * - 預設從該站的 Write-Word 起始位址寫入（base W）
 *
 * 兩種寫法：
 *  1) writeSiteData(...)      → 依 Encoder 產出的完整 bytes 寫在 base W
 *  2) writeAscii50Only(...)   → 只寫 ASCII50 區塊（25 words），避免動到前置保留 words
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcSiteWordWriter {

    private final PlcAccessService plcAccessService;
    private final PlcSiteRegistry plcSiteRegistry;
    private final PlcSiteWordEncoder siteWordEncoder;

    /**
     * 寫入整段站點 Word 資料（Encoder 產出多長就寫多長）
     */
    public void writeSiteData(int siteId, PlcSiteWordCommand command) {
        String siteName = plcSiteRegistry.getSiteNameById(siteId);
        String deviceName = plcSiteRegistry.resolvePlcDeviceNameById(siteId);
        int baseAddress = plcSiteRegistry.getWriteWordStartAddress(siteName);
        String writeAddress = "W" + PlcAddressUtils.formatAddressHexWithout0x(baseAddress);

        byte[] data = siteWordEncoder.encode(command);
        plcAccessService.writeBytes(deviceName, writeAddress, data);

        log.info("[PLC] [{}] Write PlcSiteWordCommand: {} bytes => [{}]", siteName, data.length, writeAddress);
    }

    /**
     * 只寫 ASCII50（25 個 words），從「base + PRODUCT_ID_WORD_START」開始
     * - 避免覆蓋 base 之前的保留 words
     */
    public void writeAscii50Only(int siteId, String productId) {
        String siteName = plcSiteRegistry.getSiteNameById(siteId);
        String deviceName = plcSiteRegistry.resolvePlcDeviceNameByName(siteName);
        int baseAddress   = plcSiteRegistry.getWriteWordStartAddress(siteName);
        int startAddr     = baseAddress + PlcSiteWordEncoder.PRODUCT_ID_WORD_START; // base + 6
        String writeAddr  = "W" + PlcAddressUtils.formatAddressHexWithout0x(startAddr);

        byte[] asciiBytes = siteWordEncoder.encodeAscii50Bytes(productId);
        plcAccessService.writeBytes(deviceName, writeAddr, asciiBytes);

        log.info("[PLC] [{}] Write ASCII50 ({} bytes) => [{}] value='{}'",
                siteName, asciiBytes.length, writeAddr, PlcSiteWordEncoder.sample50(productId));
    }
}
