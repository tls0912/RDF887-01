package com.czkuo.rdf88701.infra.decoder;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.plc.valueobject.TransferStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TransferDataDecoder
 * - 將 PLC Transfer 裝置的 Bit / Word 資料解析為對應結構
 * - 支援裝置狀態與回傳碼解析
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class TransferDataDecoder {

    private static final int EXPECTED_BIT_COUNT = 8;

    /**
     * 解析 Bit 區資料
     */
    public TransferBits decodeBits(byte[] bitsData) {
        boolean[] bits = PlcDataCodec.bytesToBits(bitsData, EXPECTED_BIT_COUNT);
        TransferBits result = new TransferBits();

        result.setTransferStandby(getSafeBit(bits, 0));      // B0700
        result.setProductPresent(getSafeBit(bits, 1));       // B0701
        result.setRemoveAccountReq(getSafeBit(bits, 2));     // B0702
        result.setTransferCmdAck(getSafeBit(bits, 5));       // B0705
        result.setTransferCompReq(getSafeBit(bits, 6));      // B0706
        result.setAlarm(getSafeBit(bits, 7));                // B0707

        return result;
    }

    public void decodeBits(byte[] bitsData, TransferDeviceStatus status) {
        TransferBits bits = decodeBits(bitsData);
        status.setTransferStandby(bits.isTransferStandby());
        status.setProductPresent(bits.isProductPresent());
        status.setRemoveAccountReq(bits.isRemoveAccountReq());
        status.setTransferCmdAck(bits.isTransferCmdAck());
        status.setTransferCompReq(bits.isTransferCompReq());
        status.setAlarm(bits.isAlarm());
    }

    /**
     * 解析 Word 區資料
     */
    public TransferWords decodeWords(byte[] wordsData) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);
        TransferWords result = new TransferWords();

        result.setBay(getSafeWord(words, 0));            // W1100
        result.setLevel(getSafeWord(words, 1));          // W1101
        result.setBank(getSafeWord(words, 2));           // W1102
        result.setTransferStatus(TransferStatus.fromWord(getSafeWord(words, 3))); // W1103
        result.setReturnCode(getSafeWord(words, 30));    // W111E

        // 解析 Product ID（從 W1104 ~ W111C，總共 25 組 Word，每組 2 字元）
        StringBuilder productId = new StringBuilder();
        for (int i = 4; i < 29; i++) {
            int word = getSafeWord(words, i);
            char ch1 = (char) (word & 0xFF);         // 低位元組
            char ch2 = (char) ((word >> 8) & 0xFF);  // 高位元組
            productId.append(ch1).append(ch2);
        }
        result.setProductId(productId.toString().trim());

        return result;
    }

    public void decodeWords(byte[] wordsData, TransferDeviceStatus status) {
        TransferWords words = decodeWords(wordsData);
        status.setBay(words.getBay());
        status.setLevel(words.getLevel());
        status.setBank(words.getBank());
        status.setTransferStatus(words.getTransferStatus());
        status.setReturnCode(words.getReturnCode());
        status.setProductId(words.getProductId());
    }

    public TransferDeviceStatus decodeDeviceStatus(byte[] bitsData, byte[] wordsData) {
        TransferDeviceStatus status = new TransferDeviceStatus();
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
    public static class TransferBits {
        private boolean transferStandby;
        private boolean productPresent;
        private boolean removeAccountReq;
        private boolean transferCmdAck;
        private boolean transferCompReq;
        private boolean alarm;
    }

    @Data
    public static class TransferWords {
        private int bay;
        private int level;
        private int bank;
        private TransferStatus transferStatus;
        private int returnCode;
        private String productId;
    }
}
