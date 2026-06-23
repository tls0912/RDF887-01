package com.czkuo.rdf88701.infra.adapter.plc.writer;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.PlcTransferRegistry;
import com.czkuo.rdf88701.domain.plc.command.TransferCommand;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcTransferWordCommand;
import com.czkuo.rdf88701.infra.encoder.PlcTransferWordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PlcTransferWordWriter
 * - 專責將 Transfer 任務的 Word 資料寫入 PLC
 * - 使用 PlcAccessService 封裝實際寫入流程
 * - 寫入範圍為 Transfer Word 區段（W0100 ~ W011E）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcTransferWordWriter {

    private final PlcAccessService plcAccessService;
    private final PlcTransferRegistry plcTransferRegistry;
    private final PlcTransferWordEncoder transferWordEncoder;

    /**
     * 寫入 Transfer 任務至 PLC Word 區段（例如：W0100 開始）
     */
    public void writeTransferData(int transferId, PlcTransferWordCommand command) {
        String transferName = plcTransferRegistry.getTransferNameById(transferId);
        String deviceName = plcTransferRegistry.resolvePlcDeviceNameById(transferId);
        int address = plcTransferRegistry.getWriteWordStartAddress(transferName);
        String writeAddress = "W" + PlcAddressUtils.formatAddressHexWithout0x(address);

        byte[] data = transferWordEncoder.encode(command);
        plcAccessService.writeBytes(deviceName, writeAddress, data);

        log.info("[PLC] [Transfer#{}] Write PlcTransferWordCommand: {} bytes => [{}]",
                transferId, data.length, writeAddress);
    }
}
