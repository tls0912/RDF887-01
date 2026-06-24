package com.czkuo.rdf88701.infra.encoder;

import com.czkuo.rdf88701.domain.plc.state.crane.CraneBitSignal;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcCraneBitStatus;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcCraneBitCommand;
import org.springframework.stereotype.Component;

/**
 * Crane Bit 區專用 Encoder
 * - Application 填 PlcCraneBitCommand → Encoder 組 PLC Bit 指令 boolean[]
 * - PLC 回傳 bit[] → 轉換為 CranePlcBitStatus
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class PlcCraneBitEncoder {

    private static final int BIT_ARRAY_SIZE = 64;  // 根據 PLC Bit Array 實際大小（32 or 64）

    /**
     * PC → PLC：將 PlcCraneBitCommand 編碼為 PLC bit array
     */
    public boolean[] encode(PlcCraneBitCommand cmd) {
        boolean[] bits = new boolean[BIT_ARRAY_SIZE];

        // Transfer Job Handshake
        bits[CraneBitSignal.TRANSFER_READY.getBitIndex()] = cmd.isTransferReady();
        bits[CraneBitSignal.FROM_TRANSFER_CMD_REQ.getBitIndex()] = cmd.isFromTransferCmdReq();
        bits[CraneBitSignal.FROM_TRANSFER_COMP_ACK.getBitIndex()] = cmd.isFromTransferCompAck();
        bits[CraneBitSignal.TO_TRANSFER_CMD_REQ.getBitIndex()] = cmd.isToTransferCmdReq();
        bits[CraneBitSignal.TO_TRANSFER_COMP_ACK.getBitIndex()] = cmd.isToTransferCompAck();

        // Home Return Handshake
        bits[CraneBitSignal.HOME_RETURN_REQUEST.getBitIndex()] = cmd.isHomeReturnRequest();
        bits[CraneBitSignal.REMOVE_ACCOUNT_ACK.getBitIndex()] = cmd.isRemoveAccountAck();

        return bits;
    }

    /**
     * PLC → PC：將 PLC bit array 解碼為 CranePlcBitStatus
     */
    public PlcCraneBitStatus decode(boolean[] bits) {
        PlcCraneBitStatus status = new PlcCraneBitStatus();

        // Transfer Job Handshake
        status.setFromJobHandling(getBitSafe(bits, 17));          // B0640 From Job Handling
        status.setFromTransferCmdAck(getBitSafe(bits, 18));       // B0641 From Transfer CMD Ack
        status.setFromTransferCompReq(getBitSafe(bits, 19));      // B0642 From Transfer Comp Req
        status.setToJobHandling(getBitSafe(bits, 20));            // B0643 To Job Handling
        status.setToTransferCmdAck(getBitSafe(bits, 21));         // B0644 To Transfer CMD Ack
        status.setToTransferCompReq(getBitSafe(bits, 22));        // B0645 To Transfer Comp Req

        // Home Search
        status.setHomeReturnAck(getBitSafe(bits, 27));            // B064A Home Return Ack
        status.setRemoveAccountReq(getBitSafe(bits, 28));         // B064B Remove Account Req

        return status;
    }

    /**
     * 避免 PLC bit array 長度不足時拋異常
     */
    private boolean getBitSafe(boolean[] bits, int index) {
        return index >= 0 && index < bits.length && bits[index];
    }
}
