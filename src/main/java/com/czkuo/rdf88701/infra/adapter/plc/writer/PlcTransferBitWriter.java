package com.czkuo.rdf88701.infra.adapter.plc.writer;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.PlcTransferRegistry;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperBitSignal;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferBitSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PlcTransferBitWriter
 * - 專責寫入 Transfer 指定 Bit（交握用）
 * - 支援 Transfer ID 控制
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcTransferBitWriter {

    private final PlcAccessService plcAccessService;
    private final PlcTransferRegistry plcTransferRegistry;

    /**
     * 寫入指定 Transfer Bit（offset 版）
     */
    public void writeBit(int transferId, int offset, boolean value) {
        String name = plcTransferRegistry.getTransferNameById(transferId);
        String deviceName = plcTransferRegistry.resolvePlcDeviceNameById(transferId);
        int baseAddress = plcTransferRegistry.getHandshakeBitStartAddress(name);
        int finalAddress = baseAddress + offset;
        String address = "B" + PlcAddressUtils.formatAddressHexWithout0x(finalAddress);

        plcAccessService.writeBoolean(deviceName, address, value);
        //log.debug("[PLC] [{}] Write bit: {} -> {}", name, address, value);
    }

    /**
     * 使用 enum 操作具名 bit
     */
    public void writeBit(int transferId, TransferBitSignal signal, boolean value) {
        writeBit(transferId, signal.getBitIndex(), value);
    }

    // ===========================
    // 語義化常用交握操作
    // ===========================

    public void writeTransferReady(int transferId, boolean value) {
        writeBit(transferId, TransferBitSignal.TRANSFER_READY, value);
    }

    public void writeTransferCmdReq(int transferId, boolean value) {
        writeBit(transferId, TransferBitSignal.TRANSFER_CMD_REQ, value);
    }

    public void writeTransferCompAck(int transferId, boolean value) {
        writeBit(transferId, TransferBitSignal.TRANSFER_COMP_ACK, value);
    }

    public void writeRemoveAccountAck(int transferId, boolean value) {
        writeBit(transferId, TransferBitSignal.REMOVE_ACCOUNT_ACK, value);
    }
}
