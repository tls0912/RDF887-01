package com.czkuo.rdf88701.infra.adapter.plc.writer;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.PlcGripperRegistry;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcGripperWordCommand;
import com.czkuo.rdf88701.infra.encoder.PlcGripperWordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PlcGripperWordWriter
 * - 專責將 Gripper 任務的 Word 資料寫入 PLC
 * - 使用 PlcAccessService 封裝實際寫入流程
 * - 寫入範圍為 Gripper Word 區段（W0200 ~ W021F）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcGripperWordWriter {

    private final PlcAccessService plcAccessService;
    private final PlcGripperRegistry plcGripperRegistry;
    private final PlcGripperWordEncoder gripperWordEncoder;

    /**
     * 寫入 Gripper 任務至 PLC Word 區段（例如：W0200 開始）
     *
     * @param gripperId Gripper 裝置 ID
     * @param command   要寫入的 Gripper Word 命令
     */
    public void writeGripperData(int gripperId, PlcGripperWordCommand command) {
        String gripperName = plcGripperRegistry.getGripperNameById(gripperId);
        String deviceName = plcGripperRegistry.resolvePlcDeviceNameById(gripperId);
        int address = plcGripperRegistry.getWriteWordStartAddress(gripperName);
        String writeAddress = "W" + PlcAddressUtils.formatAddressHexWithout0x(address);

        byte[] data = gripperWordEncoder.encode(command);
        plcAccessService.writeBytes(deviceName, writeAddress, data);

        log.info("[PLC] [Gripper#{}] Write PlcGripperWordCommand: {} bytes => [{}]",
                gripperId, data.length, writeAddress);
    }
}
