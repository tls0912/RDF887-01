package com.czkuo.rdf88701.infra.decoder;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneDeviceStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * CraneDataDecoder
 * - 將切割好的 Crane B 區 / W 區 byte[] 資料解析成有意義的結構
 * - 支援 Transfer Standby, Job Handling, Product ID, Location 等解析
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class CraneDataDecoder {

    private static final int EXPECTED_BIT_COUNT = 48;

    public CraneBits decodeBits(byte[] bitsData) {
        boolean[] bits = PlcDataCodec.bytesToBits(bitsData, EXPECTED_BIT_COUNT);
        CraneBits result = new CraneBits();

        result.setTransferStandby(getSafeBit(bits, 0));
        result.setCstPresent(getSafeBit(bits, 1));
        result.setReadyHandleFromCmd(getSafeBit(bits, 6));
        result.setReadyHandleToCmd(getSafeBit(bits, 7));
        result.setFromJobHandling(getSafeBit(bits, 16));
        result.setFromTransferCmdAck(getSafeBit(bits, 17));
        result.setFromTransferCompReq(getSafeBit(bits, 18));
        result.setToJobHandling(getSafeBit(bits, 19));
        result.setToTransferCmdAck(getSafeBit(bits, 20));
        result.setToTransferCompReq(getSafeBit(bits, 21));
        result.setHomeReturnAck(getSafeBit(bits, 26));
        result.setRemoveAccountReq(getSafeBit(bits, 27));

        return result;
    }

    public void decodeBits(byte[] bitsData, CraneDeviceStatus status) {
        CraneBits bits = decodeBits(bitsData);
        status.setTransferStandby(bits.isTransferStandby());
        status.setCstPresent(bits.isCstPresent());
        status.setReadyHandleFromCmd(bits.isReadyHandleFromCmd());
        status.setReadyHandleToCmd(bits.isReadyHandleToCmd());
        status.setFromJobHandling(bits.isFromJobHandling());
        status.setFromTransferCmdAck(bits.isFromTransferCmdAck());
        status.setFromTransferCompReq(bits.isFromTransferCompReq());
        status.setToJobHandling(bits.isToJobHandling());
        status.setToTransferCmdAck(bits.isToTransferCmdAck());
        status.setToTransferCompReq(bits.isToTransferCompReq());
        status.setHomeReturnAck(bits.isHomeReturnAck());
        status.setRemoveAccountReq(bits.isRemoveAccountReq());
    }

    public CraneWords decodeWords(byte[] wordsData) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);
        CraneWords result = new CraneWords();

        result.setBayPosition(getSafeWord(words, 0));
        result.setLevelPosition(getSafeWord(words, 1));
        result.setBankPosition(getSafeWord(words, 2));
        result.setDeviceStatus(getSafeWord(words, 3));
        result.setProductHeight(getSafeWord(words, 31));
        result.setFromReturnCode(getSafeWord(words, 32));
        result.setToReturnCode(getSafeWord(words, 33));
        result.setProductId(decodeProductId(Arrays.copyOfRange(words, 4, 29)));

        return result;
    }

    public void decodeWords(byte[] wordsData, CraneDeviceStatus status) {
        CraneWords words = decodeWords(wordsData);
        status.setBayPosition(words.getBayPosition());
        status.setLevelPosition(words.getLevelPosition());
        status.setBankPosition(words.getBankPosition());
        status.setDeviceStatus(words.getDeviceStatus());
        status.setProductHeight(words.getProductHeight());
        status.setFromReturnCode(words.getFromReturnCode());
        status.setToReturnCode(words.getToReturnCode());
        status.setProductId(words.getProductId());
    }

    public String decodeProductId(int[] words) {
        byte[] productIdBytes = PlcDataCodec.wordsToBytes(words);
        return PlcDataCodec.decodeString(productIdBytes, ByteOrder.LITTLE_ENDIAN).trim();
    }

    public CraneDeviceStatus decodeDeviceStatus(byte[] bitsData, byte[] wordsData) {
        CraneDeviceStatus status = new CraneDeviceStatus();
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
    public static class CraneBits {
        private boolean transferStandby;
        private boolean cstPresent;
        private boolean readyHandleFromCmd;
        private boolean readyHandleToCmd;
        private boolean fromJobHandling;
        private boolean fromTransferCmdAck;
        private boolean fromTransferCompReq;
        private boolean toJobHandling;
        private boolean toTransferCmdAck;
        private boolean toTransferCompReq;
        private boolean homeReturnAck;
        private boolean removeAccountReq;
    }

    @Data
    public static class CraneWords {
        private int bayPosition;
        private int levelPosition;
        private int bankPosition;
        private int deviceStatus;
        private int productHeight;
        private int fromReturnCode;
        private int toReturnCode;
        private String productId;
    }
}
