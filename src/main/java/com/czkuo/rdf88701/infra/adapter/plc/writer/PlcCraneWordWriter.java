package com.czkuo.rdf88701.infra.adapter.plc.writer;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.PlcCraneRegistry;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcCraneWordCommand;
import com.czkuo.rdf88701.infra.encoder.PlcCraneWordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PlcCraneWordWriter
 * 專責將 Crane 任務的 Word 資料寫入 PLC
 * 支援 FROM / TO 指令區段寫入（W0050~W008D）
 * 使用 PlcAccessService 統一封裝
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcCraneWordWriter {

    private final PlcAccessService plcAccessService;
    private final PlcCraneRegistry plcCraneRegistry;
    private final PlcCraneWordEncoder wordEncoder;

    /**
     * 寫入 FROM Word 區段（W0050~W006E）
     */
    public void writeFromTransferData(int craneId, PlcCraneWordCommand command) {
        String craneName = plcCraneRegistry.getCraneNameById(craneId);
        String deviceName = plcCraneRegistry.resolvePlcDeviceNameByCraneId(craneId);
        int address = plcCraneRegistry.getFromWordStartAddress(craneName);
        byte[] data = wordEncoder.encodeFromSection(command);
        String writeAddress = "W" + PlcAddressUtils.formatAddressHexWithout0x(address);

        plcAccessService.writeBytes(deviceName, writeAddress, data);
        log.info("[PLC] [Crane#{}] Write FROM Word section: {} ({} bytes)", craneId, writeAddress, data.length);
    }

    /**
     * 寫入 TO Word 區段（W006F~W008D）
     */
    public void writeToTransferData(int craneId, PlcCraneWordCommand command) {
        String craneName = plcCraneRegistry.getCraneNameById(craneId);
        String deviceName = plcCraneRegistry.resolvePlcDeviceNameByCraneId(craneId);
        int address = plcCraneRegistry.getToWordStartAddress(craneName);
        byte[] data = wordEncoder.encodeToSection(command);
        String writeAddress = "W" + PlcAddressUtils.formatAddressHexWithout0x(address);

        plcAccessService.writeBytes(deviceName, writeAddress, data);
        log.info("[PLC] [Crane#{}] Write TO Word section: {} ({} bytes)", craneId, writeAddress, data.length);
    }
}
