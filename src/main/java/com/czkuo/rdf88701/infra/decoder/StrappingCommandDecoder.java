package com.czkuo.rdf88701.infra.decoder;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.plc.command.StrappingCommand;
import com.czkuo.rdf88701.domain.plc.state.Strapping.StrappingCommandStatus;
import com.czkuo.rdf88701.domain.plc.valueobject.StrappingCommandMode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * StrappingCommandDecoder
 * - 解析與組裝 PC 控制區段（Bit: Bxxxx / Word: Wxxxx）
 * - 將 PLC byte[] ↔ StrappingCommandStatus 雙向轉換
 * - 專用於 Strapping 的控制指令與回應資料解析
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class StrappingCommandDecoder {

    private static final int EXPECTED_BIT_COUNT = 8;

    // ==================== Bit 區解碼 ====================

    public PlcBits decodeBits(byte[] bitsData) {
        boolean[] bits = PlcDataCodec.bytesToBits(bitsData, EXPECTED_BIT_COUNT);
        PlcBits result = new PlcBits();
        result.setStrappingReady(getSafeBit(bits, 0));        // B0200
        result.setStrappingCmdReq(getSafeBit(bits, 3));       // B0203
        result.setStrappingCompAck(getSafeBit(bits, 4));      // B0204
        return result;
    }

    public void decodeBits(byte[] bitsData, StrappingCommandStatus status) {
        PlcBits bits = decodeBits(bitsData);
        status.setStrappingReady(bits.isStrappingReady());
        status.setStrappingCmdReq(bits.isStrappingCmdReq());
        status.setStrappingCompAck(bits.isStrappingCompAck());
    }

    public byte[] encodeBits(StrappingCommandStatus status) {
        boolean[] bits = new boolean[EXPECTED_BIT_COUNT];
        bits[0] = status.isStrappingReady();
        bits[3] = status.isStrappingCmdReq();
        bits[4] = status.isStrappingCompAck();
        return PlcDataCodec.bitsToBytes(bits);
    }

    // ==================== Word 區解碼 ====================

    public PlcWords decodeWords(byte[] wordsData) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);
        PlcWords result = new PlcWords();
        result.setStrappingNo(getSafeWord(words, 0));               // W0398
        result.setStrappingCount(getSafeWord(words, 2));            // W039A
        result.setStrappingMode(StrappingCommandMode.fromWord(getSafeWord(words, 3))); // W039B
        return result;
    }

    public void decodeWords(byte[] wordsData, StrappingCommandStatus status) {
        PlcWords words = decodeWords(wordsData);

        StrappingCommand cmd = new StrappingCommand();
        cmd.setStrappingNo(words.getStrappingNo());
        cmd.setStrappingCount(words.getStrappingCount());
        cmd.setStrappingMode(words.getStrappingMode());

        status.setCommand(cmd);
    }

    public StrappingCommandStatus decodeCommandStatus(byte[] bitsData, byte[] wordsData) {
        StrappingCommandStatus status = new StrappingCommandStatus();
        decodeBits(bitsData, status);
        decodeWords(wordsData, status);
        return status;
    }

    // ==================== Word 區編碼 ====================

    public int[] encodeWords(StrappingCommandStatus status) {
        int[] words = new int[8];
        StrappingCommand cmd = status.getCommand();
        if (cmd == null) return words;

        words[0] = cmd.getStrappingNo();
        words[2] = cmd.getStrappingCount();
        words[3] = cmd.getStrappingMode() != null ? cmd.getStrappingMode().toRaw() : 0;

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
        private boolean strappingReady;
        private boolean strappingCmdReq;
        private boolean strappingCompAck;
    }

    @Data
    public static class PlcWords {
        private int strappingNo;
        private int strappingCount;
        private StrappingCommandMode strappingMode;
    }
}
