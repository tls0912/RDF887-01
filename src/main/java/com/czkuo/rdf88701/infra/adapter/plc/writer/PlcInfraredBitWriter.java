package com.czkuo.rdf88701.infra.adapter.plc.writer;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.PlcInfraredRegistry;
import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredBitSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PlcInfraredBitWriter
 * - 寫入單一 bit 至 Infrared 指定位置（交握點位）
 * - 使用 infraredId（整數）識別設備
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcInfraredBitWriter {

    private final PlcAccessService plcAccessService;
    private final PlcInfraredRegistry plcInfraredRegistry;

    /**
     * 寫入指定 bit offset（Infrared B區 base + offset）
     */
    public void writeBit(Long infraredId, int offset, boolean value) {
        String sensorName = plcInfraredRegistry.getInfraredNameById(infraredId.intValue());
        String deviceName = plcInfraredRegistry.resolvePlcDeviceNameById(infraredId.intValue());
        int baseAddress = plcInfraredRegistry.getHandshakeBitStartAddress(sensorName);
        int finalAddress = baseAddress + offset;
        String address = "B" + PlcAddressUtils.formatAddressHexWithout0x(finalAddress);

        plcAccessService.writeBoolean(deviceName, address, value);
        //log.debug("[PLC] [{}] Write bit: {} -> {}", sensorName, address, value);
    }

    /**
     * 使用 enum（InfraredBitSignal）操作點位
     */
    public void writeBit(Long infraredId, InfraredBitSignal signal, boolean value) {
        writeBit(infraredId, signal.getBitIndex(), value);
    }

    // ============================
    // 語義化交握控制 API
    // ============================

    /** 寫入 Ready Bit */
    public void writeInfraredReady(Long infraredId, boolean value) {
        writeBit(infraredId, InfraredBitSignal.INFRARED_READY, value);
    }

    /** 寫入 Command Request Bit */
    public void writeMeasureCmdReq(Long infraredId, boolean value) {
        writeBit(infraredId, InfraredBitSignal.MEASURE_CMD_REQ, value);
    }

    /** 寫入 Completion Acknowledge Bit */
    public void writeMeasureCompAck(Long infraredId, boolean value) {
        writeBit(infraredId, InfraredBitSignal.MEASURE_COMP_ACK, value);
    }
}
