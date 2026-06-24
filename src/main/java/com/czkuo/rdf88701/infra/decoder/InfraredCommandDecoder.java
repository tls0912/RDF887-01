package com.czkuo.rdf88701.infra.decoder;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.plc.command.InfraredCommand;
import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredCommandStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * InfraredCommandDecoder
 * - 專責紅外線測高設備的指令資料解析
 * - 將 PLC 傳來的 byte[] ↔ InfraredCommandStatus 進行雙向轉換
 * - 支援 Bit / Word 區資料解析與編碼
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class InfraredCommandDecoder {

    private static final int EXPECTED_BIT_COUNT = 8;

    // ==================== Bit 區解碼 ====================

    public PlcBits decodeBits(byte[] bitsData) {
        boolean[] bits = PlcDataCodec.bytesToBits(bitsData, EXPECTED_BIT_COUNT);
        PlcBits result = new PlcBits();
        result.setInfraredReady(getSafeBit(bits, 0));          // B01C8
        result.setMeasureCmdReq(getSafeBit(bits, 3));          // B01CB
        result.setMeasureCompAck(getSafeBit(bits, 4));         // B01CC
        return result;
    }

    public void decodeBits(byte[] bitsData, InfraredCommandStatus status) {
        PlcBits bits = decodeBits(bitsData);
        status.setInfraredReady(bits.isInfraredReady());
        status.setMeasureCmdReq(bits.isMeasureCmdReq());
        status.setMeasureCompAck(bits.isMeasureCompAck());
    }

    public byte[] encodeBits(InfraredCommandStatus status) {
        boolean[] bits = new boolean[EXPECTED_BIT_COUNT];
        bits[0] = status.isInfraredReady();     // B01C8
        bits[3] = status.isMeasureCmdReq();     // B01CB
        bits[4] = status.isMeasureCompAck();    // B01CC
        return PlcDataCodec.bitsToBytes(bits);
    }

    // ==================== Word 區解碼 ====================

    public PlcWords decodeWords(byte[] wordsData) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);
        PlcWords result = new PlcWords();

        result.setInfraredNo(getSafeWord(words, 0));            // W0360
        result.setTrayThickness(getSafeWord(words, 2));         // W0362

        return result;
    }

    public void decodeWords(byte[] wordsData, InfraredCommandStatus status) {
        PlcWords words = decodeWords(wordsData);

        InfraredCommand cmd = new InfraredCommand();
        cmd.setInfraredNo(words.getInfraredNo());
        cmd.setTrayThickness(words.getTrayThickness());

        status.setCommand(cmd);
    }

    public InfraredCommandStatus decodeCommandStatus(byte[] bitsData, byte[] wordsData) {
        InfraredCommandStatus status = new InfraredCommandStatus();
        decodeBits(bitsData, status);
        decodeWords(wordsData, status);
        return status;
    }

    // ==================== Word 區編碼 ====================

    public int[] encodeWords(InfraredCommandStatus status) {
        int[] words = new int[8]; // W0360 ~ W0367 共 8 word
        InfraredCommand cmd = status.getCommand();
        if (cmd == null) return words;

        words[0] = cmd.getInfraredNo();         // W0360
        words[2] = cmd.getTrayThickness();      // W0362

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
        private boolean infraredReady;
        private boolean measureCmdReq;
        private boolean measureCompAck;
    }

    @Data
    public static class PlcWords {
        private int infraredNo;
        private int trayThickness;
    }
}
