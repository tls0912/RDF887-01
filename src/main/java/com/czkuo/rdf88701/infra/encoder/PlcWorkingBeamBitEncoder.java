package com.czkuo.rdf88701.infra.encoder;

import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamBitSignal;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcWorkingBeamBitCommand;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcWorkingBeamBitStatus;
import org.springframework.stereotype.Component;

/**
 * Working Beam Bit 區專用 Encoder
 * - Application 填 PlcWorkingBeamBitCommand → 編碼為 PLC bit array（boolean[]）
 * - PLC 回傳 bit[] → 轉換為 WorkingBeamPlcBitStatus
 */
@Component
public class PlcWorkingBeamBitEncoder {

    private static final int BIT_ARRAY_SIZE = 8;

    /**
     * PC → PLC：將 PlcWorkingBeamBitCommand 編碼為 boolean[]
     */
    public boolean[] encode(PlcWorkingBeamBitCommand cmd) {
        boolean[] bits = new boolean[BIT_ARRAY_SIZE];

        bits[WorkingBeamBitSignal.TRANSFER_READY.getBitIndex()] = cmd.isWorkingBeamReady();
        bits[WorkingBeamBitSignal.TRANSFER_CMD_REQ.getBitIndex()] = cmd.isTransferCmdReq();
        bits[WorkingBeamBitSignal.TRANSFER_COMP_ACK.getBitIndex()] = cmd.isTransferCompAck();

        return bits;
    }

    /**
     * PLC → PC：將 boolean[] 解碼為 WorkingBeamPlcBitStatus
     */
    public PlcWorkingBeamBitStatus decode(boolean[] bits) {
        PlcWorkingBeamBitStatus status = new PlcWorkingBeamBitStatus();

        status.setWorkingBeamStandby(getBitSafe(bits, 0));          // B0748
        status.setTransferCmdAck(getBitSafe(bits, 5));              // B074D
        status.setTransferCompReq(getBitSafe(bits, 6));             // B074E
        status.setAlarm(getBitSafe(bits, 7));                       // B074F

        return status;
    }

    /**
     * 避免 PLC bit array 越界異常
     */
    private boolean getBitSafe(boolean[] bits, int index) {
        return index >= 0 && index < bits.length && bits[index];
    }
}
