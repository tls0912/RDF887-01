package com.czkuo.rdf88701.infra.decoder;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.domain.plc.valueobject.GripperStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * GripperDataDecoder
 * - 專門將切割好的 Gripper B 區 / W 區 byte[] 資料解析成有意義的結構
 * - 支援 Ready, Product Present, Alarm, Product ID, Bay/Level/Bank 等解析
 */
@Slf4j
@Component
public class GripperDataDecoder {

    // ==================== B 區 (Bit 區域) ====================

    /**
     * 解析 Gripper 的 bit 資料
     */
    public GripperBits decodeBits(byte[] bitsData, int expectedBitCount) {
        boolean[] bits = PlcDataCodec.bytesToBits(bitsData, expectedBitCount);
        GripperBits result = new GripperBits();

        result.setReady(getSafeBit(bits, 0));            // bit[0]: Ready
        result.setProductPresent(getSafeBit(bits, 1));   // bit[1]: Product Present
        result.setRemoveAccountReq(getSafeBit(bits, 2)); // bit[2]: Remove Account Req
        result.setTransferCmdAck(getSafeBit(bits, 5));   // bit[5]: Transfer CMD Ack
        result.setTransferCompReq(getSafeBit(bits, 6));  // bit[6]: Transfer Comp Req
        result.setAlarm(getSafeBit(bits, 7));            // bit[7]: Alarm

        return result;
    }

    /**
     * 將 bit 資料解到 GripperDeviceStatus
     */
    public void decodeBits(byte[] bitsData, GripperDeviceStatus status) {
        GripperBits bits = decodeBits(bitsData, 8);
        status.setTransferStandby(bits.isReady());
        status.setProductPresent(bits.isProductPresent());
        status.setRemoveAccountReq(bits.isRemoveAccountReq());
        status.setTransferCmdAck(bits.isTransferCmdAck());
        status.setTransferCompReq(bits.isTransferCompReq());
        status.setAlarm(bits.isAlarm());
    }

    // ==================== W 區 (Word 區域) ====================

    /**
     * 解析 Gripper 的 word 資料
     */
    public GripperWords decodeWords(byte[] wordsData) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);
        GripperWords result = new GripperWords();

        result.setBay(getSafeWord(words, 0));    // word[0]: Bay
        result.setLevel(getSafeWord(words, 1));  // word[1]: Level
        result.setBank(getSafeWord(words, 2));   // word[2]: Bank
        result.setGripperStatus(GripperStatus.fromWord(getSafeWord(words, 3)));   // word[3]: DeviceStatus
        result.setReturnCode(getSafeWord(words, 30));    // word[30]: ReturnCode

        return result;
    }

    /**
     * 將 word 資料解到 GripperDeviceStatus
     * （自動包含 ProductId）
     */
    public void decodeWords(byte[] wordsData, GripperDeviceStatus status) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);

        GripperWords basic = decodeWords(wordsData);
        status.setBay(basic.getBay());
        status.setLevel(basic.getLevel());
        status.setBank(basic.getBank());
        status.setGripperStatus(basic.getGripperStatus());
        status.setReturnCode(basic.getReturnCode());

        // 同時解出 ProductId
        String productId = decodeProductId(words);
        status.setProductId(productId);
    }

    // ==================== Product ID 專區 ====================

    /**
     * 從 Words 陣列解析出 ProductId（word[4] ~ word[28]）
     */
    public String decodeProductId(int[] words) {
        if (words == null || words.length < 29) {
            throw new IllegalArgumentException("Invalid words array for decoding ProductId");
        }
        // 取 word[4] ~ word[28] 共 25 個 word，轉成新的 int[]
        int[] productIdWords = Arrays.copyOfRange(words, 4, 29);

        // 直接用 PlcDataCodec.wordsToBytes 轉成 byte[]
        byte[] productIdBytes = PlcDataCodec.wordsToBytes(productIdWords);

        // 再 decode 成字串
        return PlcDataCodec.decodeString(productIdBytes, ByteOrder.LITTLE_ENDIAN).trim();
    }

    /**
     * 將原始 byte[] 解成字串
     */
    public String decodeProductId(byte[] productIdData) {
        return PlcDataCodec.decodeString(productIdData, ByteOrder.LITTLE_ENDIAN).trim();
    }

    // ==================== 綜合 ====================

    /**
     * 綜合解析（一次解析 bits + words + productId）
     */
    public GripperDeviceStatus decodeDeviceStatus(byte[] bitsData, byte[] wordsData) {
        GripperDeviceStatus status = new GripperDeviceStatus();
        decodeBits(bitsData, status);
        decodeWords(wordsData, status);
        return status;
    }

    // ==================== Helper ====================

    private boolean getSafeBit(boolean[] bits, int index) {
        if (index >= 0 && index < bits.length) {
            return bits[index];
        }
        return false;
    }

    private int getSafeWord(int[] words, int index) {
        if (index >= 0 && index < words.length) {
            return words[index];
        }
        return 0;
    }

    // ==================== DTO ====================

    @Data
    public static class GripperBits {
        private boolean ready;
        private boolean productPresent;
        private boolean removeAccountReq;
        private boolean transferCmdAck;
        private boolean transferCompReq;
        private boolean alarm;
    }

    @Data
    public static class GripperWords {
        private int bay;
        private int level;
        private int bank;
        private GripperStatus gripperStatus;
        private int returnCode;
        private String productId;
    }
}
