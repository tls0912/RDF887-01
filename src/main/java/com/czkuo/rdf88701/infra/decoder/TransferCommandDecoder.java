package com.czkuo.rdf88701.infra.decoder;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.plc.command.TransferCommand;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferCommandStatus;
import com.czkuo.rdf88701.domain.plc.valueobject.TransferCommandType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TransferCommandDecoder
 * - 專責 Transfer 的指令資料解析
 * - 將 PLC byte[] ↔ TransferCommandStatus 進行雙向轉換
 * - 支援 Bit 與 Word 區資料的解析與編碼
 */
@Slf4j
@Component
public class TransferCommandDecoder {

    private static final int EXPECTED_BIT_COUNT = 8;
    private static final int PRODUCT_ID_WORD_START = 6;  // W0106 ~ W011E
    private static final int PRODUCT_ID_WORD_COUNT = 25;

    // ==================== Bit 區解碼 ====================

    public PlcBits decodeBits(byte[] bitsData) {
        boolean[] bits = PlcDataCodec.bytesToBits(bitsData, EXPECTED_BIT_COUNT);
        PlcBits result = new PlcBits();
        result.setTransferReady(getSafeBit(bits, 0));     // B0100
        result.setRemoveAccountAck(getSafeBit(bits, 2));  // B0102
        result.setTransferCmdReq(getSafeBit(bits, 5));    // B0105
        result.setTransferCompAck(getSafeBit(bits, 6));   // B0106
        return result;
    }

    public void decodeBits(byte[] bitsData, TransferCommandStatus status) {
        PlcBits bits = decodeBits(bitsData);
        status.setTransferReady(bits.isTransferReady());
        status.setRemoveAccountAck(bits.isRemoveAccountAck());
        status.setTransferCmdReq(bits.isTransferCmdReq());
        status.setTransferCompAck(bits.isTransferCompAck());
    }

    public byte[] encodeBits(TransferCommandStatus status) {
        boolean[] bits = new boolean[EXPECTED_BIT_COUNT];
        bits[0] = status.isTransferReady();
        bits[2] = status.isRemoveAccountAck();
        bits[5] = status.isTransferCmdReq();
        bits[6] = status.isTransferCompAck();
        return PlcDataCodec.bitsToBytes(bits);
    }

    // ==================== Word 區解碼 ====================

    public PlcWords decodeWords(byte[] wordsData) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);
        PlcWords result = new PlcWords();

        result.setTransferNo(getSafeWord(words, 0));                    // W0100
        result.setCommandType(TransferCommandType.fromWord(words[1]));        // W0101
        result.setLocationBank(getSafeWord(words, 3));                  // W0103
        result.setLocationBay(getSafeWord(words, 4));                   // W0104
        result.setLocationLevel(getSafeWord(words, 5));                 // W0105

        // Product ID 解碼（W0106 ~ W011E）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PRODUCT_ID_WORD_COUNT; i++) {
            int word = getSafeWord(words, PRODUCT_ID_WORD_START + i);
            char ch1 = (char) (word & 0xFF);         // 低位元組
            char ch2 = (char) ((word >> 8) & 0xFF);  // 高位元組
            sb.append(ch1).append(ch2);
        }
        result.setProductId(sb.toString().trim());

        return result;
    }

    public void decodeWords(byte[] wordsData, TransferCommandStatus status) {
        PlcWords words = decodeWords(wordsData);

        TransferCommand cmd = new TransferCommand();
        cmd.setTransferNo(words.getTransferNo());
        cmd.setTaskType(words.getCommandType());
        cmd.setLocationBank(words.getLocationBank());
        cmd.setLocationBay(words.getLocationBay());
        cmd.setLocationLevel(words.getLocationLevel());
        cmd.setProductId(words.getProductId());

        status.setCommand(cmd);
    }

    public TransferCommandStatus decodeCommandStatus(byte[] bitsData, byte[] wordsData) {
        TransferCommandStatus status = new TransferCommandStatus();
        decodeBits(bitsData, status);
        decodeWords(wordsData, status);
        return status;
    }

    // ==================== Word 區編碼 ====================

    public int[] encodeWords(TransferCommandStatus status) {
        int[] words = new int[6 + PRODUCT_ID_WORD_COUNT];  // 共 31 word
        TransferCommand cmd = status.getCommand();
        if (cmd == null) return words;

        words[0] = cmd.getTransferNo();                                  // W0100
        words[1] = cmd.getTaskType() != null ? cmd.getTaskType().toRaw() : 0; // W0101
        words[3] = cmd.getLocationBank();                                // W0103
        words[4] = cmd.getLocationBay();                                 // W0104
        words[5] = cmd.getLocationLevel();                               // W0105

        // Product ID 編碼（W0106 ~ W011E）
        String productId = cmd.getProductId() != null ? cmd.getProductId() : "";
        productId = String.format("%-50s", productId);  // 補滿至 50 字元
        for (int i = 0; i < PRODUCT_ID_WORD_COUNT; i++) {
            char ch1 = productId.charAt(i * 2);
            char ch2 = productId.charAt(i * 2 + 1);
            words[PRODUCT_ID_WORD_START + i] = ((ch1 & 0xFF) << 8) | (ch2 & 0xFF);
        }

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
        private boolean removeAccountAck;
        private boolean transferCmdReq;
        private boolean transferCompAck;
    }

    @Data
    public static class PlcWords {
        private int transferNo;
        private TransferCommandType commandType;
        private int locationBank;
        private int locationBay;
        private int locationLevel;
        private String productId;
    }
}
