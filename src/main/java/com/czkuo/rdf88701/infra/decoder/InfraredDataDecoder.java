package com.czkuo.rdf88701.infra.decoder;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredDeviceStatus;
import com.czkuo.rdf88701.domain.plc.valueobject.InfraredStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * InfraredDataDecoder
 * - 將 PLC 紅外線設備的 Bit / Word 資料解析為 InfraredDeviceStatus
 */
@Slf4j
@Component
public class InfraredDataDecoder {

    private static final int EXPECTED_BIT_COUNT = 8;

    /**
     * 解析 Bit 區資料
     */
    public InfraredBits decodeBits(byte[] bitsData) {
        boolean[] bits = PlcDataCodec.bytesToBits(bitsData, EXPECTED_BIT_COUNT);
        InfraredBits result = new InfraredBits();

        result.setInfraredStandby(getSafeBit(bits, 0));        // B07C8
        result.setMeasureHeightCmdAck(getSafeBit(bits, 3));    // B07CB
        result.setMeasureHeightCompReq(getSafeBit(bits, 4));   // B07CC
        result.setAlarm(getSafeBit(bits, 7));                  // B07CF

        return result;
    }

    public void decodeBits(byte[] bitsData, InfraredDeviceStatus status) {
        InfraredBits bits = decodeBits(bitsData);
        status.setInfraredStandby(bits.isInfraredStandby());
        status.setMeasureCmdAck(bits.isMeasureHeightCmdAck());
        status.setMeasureCompReq(bits.isMeasureHeightCompReq());
        status.setAlarm(bits.isAlarm());
    }

    /**
     * 解析 Word 區資料
     */
    public InfraredWords decodeWords(byte[] wordsData) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);
        InfraredWords result = new InfraredWords();

        result.setProductHeight1(getSafeWord(words, 0));          // W1360
        result.setProductHeight2(getSafeWord(words, 1));          // W1361
        result.setProductQuantity(getSafeWord(words, 2));         // W1362

        int statusWord = getSafeWord(words, 3);                   // W1363
        result.setInfraredStatus(InfraredStatus.fromWord(statusWord));

        result.setReturnCode(getSafeWord(words, 6));              // W1366

        return result;
    }

    public void decodeWords(byte[] wordsData, InfraredDeviceStatus status) {
        InfraredWords words = decodeWords(wordsData);
        status.setProductHeight1(words.getProductHeight1());
        status.setProductHeight2(words.getProductHeight2());
        status.setProductQuantity(words.getProductQuantity());
        status.setInfraredStatus(words.getInfraredStatus());
        status.setReturnCode(words.getReturnCode());
    }

    public InfraredDeviceStatus decodeDeviceStatus(byte[] bitsData, byte[] wordsData) {
        InfraredDeviceStatus status = new InfraredDeviceStatus();
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
    public static class InfraredBits {
        private boolean infraredStandby;
        private boolean measureHeightCmdAck;
        private boolean measureHeightCompReq;
        private boolean alarm;
    }

    @Data
    public static class InfraredWords {
        private int productQuantity;
        private int productHeight1;
        private int productHeight2;
        private InfraredStatus infraredStatus;
        private int returnCode;
    }
}
