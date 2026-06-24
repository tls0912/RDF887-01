package com.czkuo.rdf88701.infra.decoder;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.plc.state.Strapping.StrappingDeviceStatus;
import com.czkuo.rdf88701.domain.plc.valueobject.StrappingStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * StrappingDataDecoder
 * - 將切割好的 Strapping 的 B/W 區段 byte[] 資料解析成對應結構
 * - 支援 DeviceStatus 與 RetCode 的解析
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class StrappingDataDecoder {

    private static final int EXPECTED_BIT_COUNT = 8;

    /**
     * 解析 Bit 區資料
     */
    public StrappingBits decodeBits(byte[] bitsData) {
        boolean[] bits = PlcDataCodec.bytesToBits(bitsData, EXPECTED_BIT_COUNT);
        StrappingBits result = new StrappingBits();

        result.setStrappingStandby(getSafeBit(bits, 0));      // B0800
        result.setStrappingCmdAck(getSafeBit(bits, 3));       // B0803
        result.setStrappingCompReq(getSafeBit(bits, 4));      // B0804
        result.setAlarm(getSafeBit(bits, 7));                 // B0807

        return result;
    }

    public void decodeBits(byte[] bitsData, StrappingDeviceStatus status) {
        StrappingBits bits = decodeBits(bitsData);
        status.setStrappingStandby(bits.isStrappingStandby());
        status.setStrappingCmdAck(bits.isStrappingCmdAck());
        status.setStrappingCompReq(bits.isStrappingCompReq());
        status.setAlarm(bits.isAlarm());
    }

    /**
     * 解析 Word 區資料
     */
    public StrappingWords decodeWords(byte[] wordsData) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);
        StrappingWords result = new StrappingWords();

        result.setStrappingStatus(StrappingStatus.fromWord(getSafeWord(words, 3)));  // W139B
        result.setReturnCode(getSafeWord(words, 6));                                 // W139E

        return result;
    }

    public void decodeWords(byte[] wordsData, StrappingDeviceStatus status) {
        StrappingWords words = decodeWords(wordsData);
        status.setStrappingStatus(words.getStrappingStatus());
        status.setReturnCode(words.getReturnCode());
    }

    public StrappingDeviceStatus decodeDeviceStatus(byte[] bitsData, byte[] wordsData) {
        StrappingDeviceStatus status = new StrappingDeviceStatus();
        decodeBits(bitsData, status);
        decodeWords(wordsData, status);
        return status;
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
    public static class StrappingBits {
        private boolean strappingStandby;
        private boolean strappingCmdAck;
        private boolean strappingCompReq;
        private boolean alarm;
    }

    @Data
    public static class StrappingWords {
        private StrappingStatus strappingStatus;
        private int returnCode;
    }
}
