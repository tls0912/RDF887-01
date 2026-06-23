package com.czkuo.rdf88701.infra.adapter.plc.writer;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.PlcCraneRegistry;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneBitSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PlcCraneBitWriter
 * 寫入單一 bit 至 Crane 指定位置（交握用）
 * 使用 craneId（整數）識別設備
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcCraneBitWriter {

    private final PlcAccessService plcAccessService;
    private final PlcCraneRegistry plcCraneRegistry;

    /**
     * 寫入指定的握手 Bit
     * @param craneId  Crane ID
     * @param offset   該 Crane bit 區的 offset（如 0 表示 B0030）
     * @param value    true/false
     */
    public void writeBit(int craneId, int offset, boolean value) {
        String craneName = plcCraneRegistry.getCraneById(craneId).getName();
        String deviceName = plcCraneRegistry.resolvePlcDeviceNameByCraneName(craneName);
        int baseAddress = plcCraneRegistry.getHandshakeBitStartAddress(craneName);
        int finalAddress = baseAddress + offset;
        String address = "B" + PlcAddressUtils.formatAddressHexWithout0x(finalAddress);

        plcAccessService.writeBoolean(deviceName, address, value);
        //log.debug("[PLC] [{}] Write bit: {} -> {}", craneName, address, value);
    }

    /**
     * 使用 enum 名義化 API 操作點位
     */
    public void writeBit(int craneId, CraneBitSignal signal, boolean value) {
        writeBit(craneId, signal.getBitIndex(), value);
    }

    // ===========================
    // 包裝常用交握操作（語義化）
    // ===========================
    public void writeTransferReady(int craneId, boolean value) {
        writeBit(craneId, CraneBitSignal.TRANSFER_READY, value);
    }

    public void writeFromTransferCmdReq(int craneId, boolean value) {
        writeBit(craneId, CraneBitSignal.FROM_TRANSFER_CMD_REQ, value);
    }

    public void writeFromTransferCompAck(int craneId, boolean value) {
        writeBit(craneId, CraneBitSignal.FROM_TRANSFER_COMP_ACK, value);
    }

    public void writeToTransferCmdReq(int craneId, boolean value) {
        writeBit(craneId, CraneBitSignal.TO_TRANSFER_CMD_REQ, value);
    }

    public void writeToTransferCompAck(int craneId, boolean value) {
        writeBit(craneId, CraneBitSignal.TO_TRANSFER_COMP_ACK, value);
    }

    public void writeHomeReturnRequest(int craneId, boolean value) {
        writeBit(craneId, CraneBitSignal.HOME_RETURN_REQUEST, value);
    }

    public void writeRemoveAccountAck(int craneId, boolean value) {
        writeBit(craneId, CraneBitSignal.REMOVE_ACCOUNT_ACK, value);
    }
}
