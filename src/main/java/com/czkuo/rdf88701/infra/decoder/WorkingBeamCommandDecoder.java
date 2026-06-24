package com.czkuo.rdf88701.infra.decoder;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.plc.command.WorkingBeamCommand;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamCommandStatus;
import com.czkuo.rdf88701.domain.plc.valueobject.WorkingBeamCommandMeta;
import com.czkuo.rdf88701.domain.plc.valueobject.WorkingBeamCommandType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WorkingBeamCommandDecoder
 * - 解析與組裝 PC 控制區段（Bit: Bxxxx / Word: Wxxxx）
 * - 將 PLC byte[] ↔ WorkingBeamCommandStatus 雙向轉換
 * - 專用於 Working Beam 的控制指令與回應資料解析
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class WorkingBeamCommandDecoder {

    private static final int EXPECTED_BIT_COUNT = 8;

    // ==================== Bit 區解碼 ====================

    public PlcBits decodeBits(byte[] bitsData) {
        boolean[] bits = PlcDataCodec.bytesToBits(bitsData, EXPECTED_BIT_COUNT);
        PlcBits result = new PlcBits();
        result.setTransferReady(getSafeBit(bits, 0));
        result.setTransferCmdReq(getSafeBit(bits, 5));
        result.setTransferCompAck(getSafeBit(bits, 6));
        return result;
    }

    public void decodeBits(byte[] bitsData, WorkingBeamCommandStatus status) {
        PlcBits bits = decodeBits(bitsData);
        status.setTransferReady(bits.isTransferReady());
        status.setTransferCmdReq(bits.isTransferCmdReq());
        status.setTransferCompAck(bits.isTransferCompAck());
    }

    public byte[] encodeBits(WorkingBeamCommandStatus status) {
        boolean[] bits = new boolean[EXPECTED_BIT_COUNT];
        bits[0] = status.isTransferReady();
        bits[5] = status.isTransferCmdReq();
        bits[6] = status.isTransferCompAck();
        return PlcDataCodec.bitsToBytes(bits);
    }

    // ==================== Word 區解碼 ====================

    public PlcWords decodeWords(byte[] wordsData) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);
        PlcWords result = new PlcWords();
        result.setTransferNo(getSafeWord(words, 0));
        result.setCommandType(WorkingBeamCommandType.fromWord(words[1]));
        result.setMeta(WorkingBeamCommandMeta.fromWord(words[2]));
        return result;
    }

    public void decodeWords(byte[] wordsData, WorkingBeamCommandStatus status) {
        PlcWords words = decodeWords(wordsData);

        WorkingBeamCommand cmd = new WorkingBeamCommand();
        cmd.setTransferNo(words.getTransferNo());
        cmd.setCommandType(words.getCommandType());
        cmd.setCommandMeta(words.getMeta());

        status.setCommand(cmd);
    }

    public WorkingBeamCommandStatus decodeCommandStatus(byte[] bitsData, byte[] wordsData) {
        WorkingBeamCommandStatus status = new WorkingBeamCommandStatus();
        decodeBits(bitsData, status);
        decodeWords(wordsData, status);
        return status;
    }

    // ==================== Word 區編碼 ====================

    public int[] encodeWords(WorkingBeamCommandStatus status) {
        int[] words = new int[8];
        WorkingBeamCommand cmd = status.getCommand();
        if (cmd == null) return words;

        words[0] = cmd.getTransferNo();
        words[1] = cmd.getCommandType() != null ? cmd.getCommandType().toRaw() : 0;
        words[2] = cmd.getCommandMeta() != null ? cmd.getCommandMeta().toRaw() : 0;

        return words;
    }

    // ==================== Helper ====================

    private boolean getSafeBit(boolean[] bits, int index) {
        return index >= 0 && index < bits.length && bits[index];
    }

    private int getSafeWord(int[] words, int index) {
        return (index >= 0 && index < words.length) ? words[index] : 0;
    }

    // ==================== DTO ====================

    @Data
    public static class PlcBits {
        private boolean transferReady;
        private boolean transferCmdReq;
        private boolean transferCompAck;
    }

    @Data
    public static class PlcWords {
        private int transferNo;
        private WorkingBeamCommandType commandType;
        private WorkingBeamCommandMeta meta;
    }
}
