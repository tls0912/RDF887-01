package com.czkuo.rdf88701.infra.adapter.plc.writer;

import com.czkuo.rdf88701.application.assembler.PlcInfraredWordCommand;
import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.PlcInfraredRegistry;
import com.czkuo.rdf88701.infra.encoder.PlcInfraredWordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PlcInfraredWordWriter
 * 專責將 Infrared 任務的 Word 資料寫入 PLC
 * 對應區段：W0360~W0367（8 Words）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcInfraredWordWriter {

    private final PlcAccessService plcAccessService;
    private final PlcInfraredRegistry plcInfraredRegistry;
    private final PlcInfraredWordEncoder wordEncoder;

    /**
     * 寫入 Infrared 指令 Word 資料（Measure No, TaskType, ...）
     */
    public void writeMeasureData(Long infraredId, PlcInfraredWordCommand command) {
        String sensorName = plcInfraredRegistry.getInfraredNameById(infraredId.intValue());
        String deviceName = plcInfraredRegistry.resolvePlcDeviceNameById(infraredId.intValue());
        int startAddress = plcInfraredRegistry.getWriteWordStartAddress(sensorName);
        byte[] data = wordEncoder.encode(command);
        String writeAddress = "W" + PlcAddressUtils.formatAddressHexWithout0x(startAddress);

        plcAccessService.writeBytes(deviceName, writeAddress, data);
        log.info("[PLC] [Infrared#{}] Write Word section: {} ({} bytes)", infraredId, writeAddress, data.length);
    }
}
