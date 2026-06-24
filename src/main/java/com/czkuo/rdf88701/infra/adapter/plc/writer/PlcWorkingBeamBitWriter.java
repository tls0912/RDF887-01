package com.czkuo.rdf88701.infra.adapter.plc.writer;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.PlcWorkingBeamRegistry;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamBitSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PlcWorkingBeamBitWriter
 * - 寫入單一 bit 至 WorkingBeam 指定位置（交握點位）
 * - 使用 workingBeamId（整數）識別設備
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcWorkingBeamBitWriter {

    private final PlcAccessService plcAccessService;
    private final PlcWorkingBeamRegistry plcWorkingBeamRegistry;

    /**
     * 寫入指定 bit offset（Working Beam B區 base + offset）
     */
    public void writeBit(int beamId, int offset, boolean value) {
        String beamName = plcWorkingBeamRegistry.getWorkingBeamNameById(beamId);
        String deviceName = plcWorkingBeamRegistry.resolvePlcDeviceNameById(beamId);
        int baseAddress = plcWorkingBeamRegistry.getHandshakeBitStartAddress(beamName);
        int finalAddress = baseAddress + offset;
        String address = "B" + PlcAddressUtils.formatAddressHexWithout0x(finalAddress);

        plcAccessService.writeBoolean(deviceName, address, value);
        //log.debug("[PLC] [{}] Write bit: {} -> {}", beamName, address, value);
    }

    /**
     * 使用 enum（WorkingBeamBitSignal）操作點位
     */
    public void writeBit(int beamId, WorkingBeamBitSignal signal, boolean value) {
        writeBit(beamId, signal.getBitIndex(), value);
    }

    // ============================
    // 語義化交握控制 API
    // ============================

    /** 寫入 Ready Bit（B0148） */
    public void writeTransferReady(int beamId, boolean value) {
        writeBit(beamId, WorkingBeamBitSignal.TRANSFER_READY, value);
    }

    /** 寫入 Command Request Bit（B014D） */
    public void writeTransferCmdReq(int beamId, boolean value) {
        writeBit(beamId, WorkingBeamBitSignal.TRANSFER_CMD_REQ, value);
    }

    /** 寫入 Completion Acknowledge Bit（B014E） */
    public void writeTransferCompAck(int beamId, boolean value) {
        writeBit(beamId, WorkingBeamBitSignal.TRANSFER_COMP_ACK, value);
    }
}
