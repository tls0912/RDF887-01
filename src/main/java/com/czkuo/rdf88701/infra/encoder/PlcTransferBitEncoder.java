package com.czkuo.rdf88701.infra.encoder;

import com.czkuo.rdf88701.domain.plc.state.transfer.TransferBitSignal;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcTransferBitCommand;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcTransferBitStatus;
import org.springframework.stereotype.Component;

/**
 * PlcTransferBitEncoder
 * - 專責將 Transfer Bit 指令與狀態進行編碼與解碼
 * - PC → PLC：PlcTransferBitCommand → boolean[]
 * - PLC → PC：boolean[] → PlcTransferBitStatus
 */
@Component
public class PlcTransferBitEncoder {

    private static final int BIT_ARRAY_SIZE = 8;

    /**
     * 將指令物件編碼為 boolean[]
     */
    public boolean[] encode(PlcTransferBitCommand cmd) {
        boolean[] bits = new boolean[BIT_ARRAY_SIZE];

        bits[TransferBitSignal.TRANSFER_READY.getBitIndex()]    = cmd.isTransferReady();
        bits[TransferBitSignal.TRANSFER_CMD_REQ.getBitIndex()]  = cmd.isTransferCmdReq();
        bits[TransferBitSignal.TRANSFER_COMP_ACK.getBitIndex()] = cmd.isTransferCompAck();

        return bits;
    }

    /**
     * 將 PLC 傳回的 boolean[] 解碼為狀態物件
     */
    public PlcTransferBitStatus decode(boolean[] bits) {
        PlcTransferBitStatus status = new PlcTransferBitStatus();

        status.setTransferStandby(getBitSafe(bits, 0));     // B0748
        status.setTransferCmdAck(getBitSafe(bits, 5));      // B074D
        status.setTransferCompReq(getBitSafe(bits, 6));     // B074E
        status.setAlarm(getBitSafe(bits, 7));               // B074F

        return status;
    }

    /**
     * 避免越界的 Bit 安全存取方法
     */
    private boolean getBitSafe(boolean[] bits, int index) {
        return index >= 0 && index < bits.length && bits[index];
    }
}
