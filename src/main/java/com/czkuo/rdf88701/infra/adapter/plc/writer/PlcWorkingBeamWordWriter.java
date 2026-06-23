package com.czkuo.rdf88701.infra.adapter.plc.writer;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.PlcWorkingBeamRegistry;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcWorkingBeamWordCommand;
import com.czkuo.rdf88701.infra.encoder.PlcWorkingBeamWordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PlcWorkingBeamWordWriter
 * 專責將 WorkingBeam 任務的 Word 資料寫入 PLC
 * 對應區段：W0220~W0227（8 Words）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcWorkingBeamWordWriter {

    private final PlcAccessService plcAccessService;
    private final PlcWorkingBeamRegistry plcWorkingBeamRegistry;
    private final PlcWorkingBeamWordEncoder wordEncoder;

    /**
     * 寫入 WorkingBeam 指令 Word 資料（Transfer No, Type, Direction 等）
     */
    public void writeTransferData(int beamId, PlcWorkingBeamWordCommand command) {
        String beamName = plcWorkingBeamRegistry.getWorkingBeamNameById(beamId);
        String deviceName = plcWorkingBeamRegistry.resolvePlcDeviceNameById(beamId);
        int startAddress = plcWorkingBeamRegistry.getWriteWordStartAddress(beamName);
        byte[] data = wordEncoder.encode(command);
        String writeAddress = "W" + PlcAddressUtils.formatAddressHexWithout0x(startAddress);

        plcAccessService.writeBytes(deviceName, writeAddress, data);
        log.info("[PLC] [WorkingBeam#{}] Write Word section: {} ({} bytes)", beamId, writeAddress, data.length);
    }
}
