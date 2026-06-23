package com.czkuo.rdf88701.infra.decoder;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.plc.command.GripperCommand;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperCommandStatus;
import com.czkuo.rdf88701.domain.plc.valueobject.GripperCommandType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * GripperCommandDecoder
 * - 專責解析 Gripper 裝置的指令區資料（Write Bit / Write Word）
 * - 將 PLC byte[] ↔ GripperCommandStatus 進行雙向轉換
 * - 支援 Bit 區旗標與 Word 區結構（TransferNo、Location、Product ID）
 */
@Slf4j
@Component
public class GripperCommandDecoder {

    private static final int EXPECTED_BIT_COUNT = 8;
    private static final int PRODUCT_ID_WORD_START = 6;  // 對應 W0266 開始
    private static final int PRODUCT_ID_WORD_COUNT = 25; // W0266 ~ W027E 共 25 字，50 字元

    // ==================== Bit 區解碼（B0188 ~ B018F） ====================

    public PlcBits decodeBits(byte[] bitsData) {
        boolean[] bits = PlcDataCodec.bytesToBits(bitsData, EXPECTED_BIT_COUNT);
        PlcBits result = new PlcBits();
        result.setGripperReady(getSafeBit(bits, 0));       // B0188 - Gripper Ready
        result.setRemoveAccountAck(getSafeBit(bits, 2));   // B018A - Remove Account Ack
        result.setTransferCmdReq(getSafeBit(bits, 5));     // B018D - Transfer CMD Req
        result.setTransferCompAck(getSafeBit(bits, 6));    // B018E - Transfer Comp Ack
        return result;
    }

    public void decodeBits(byte[] bitsData, GripperCommandStatus status) {
        PlcBits bits = decodeBits(bitsData);
        status.setTransferReady(bits.isGripperReady());
        status.setRemoveAccountAck(bits.isRemoveAccountAck());
        status.setTransferCmdReq(bits.isTransferCmdReq());
        status.setTransferCompAck(bits.isTransferCompAck());
    }

    public byte[] encodeBits(GripperCommandStatus status) {
        boolean[] bits = new boolean[EXPECTED_BIT_COUNT];
        bits[0] = status.isTransferReady();      // B0188
        bits[2] = status.isRemoveAccountAck();   // B018A
        bits[5] = status.isTransferCmdReq();     // B018D
        bits[6] = status.isTransferCompAck();    // B018E
        return PlcDataCodec.bitsToBytes(bits);
    }

    // ==================== Word 區解碼（W0260 ~ W027F） ====================

    public PlcWords decodeWords(byte[] wordsData) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);
        PlcWords result = new PlcWords();

        result.setTransferNo(getSafeWord(words, 0));                        // W0260
        result.setCommandType(GripperCommandType.fromWord(words[1]));             // W0261
        result.setTrayHeight(getSafeWord(words, 2));                        // W0262
        result.setLocationBank(getSafeWord(words, 3));                      // W0263
        result.setLocationBay(getSafeWord(words, 4));                       // W0264
        result.setLocationLevel(getSafeWord(words, 5));                     // W0265

        // Product ID 字元組合（W0266 ~ W027E）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PRODUCT_ID_WORD_COUNT; i++) {
            int word = getSafeWord(words, PRODUCT_ID_WORD_START + i);
            char ch1 = (char) (word & 0xFF);         // 低位元
            char ch2 = (char) ((word >> 8) & 0xFF);  // 高位元
            sb.append(ch1).append(ch2);
        }
        result.setProductId(sb.toString().trim());

        return result;
    }

    public void decodeWords(byte[] wordsData, GripperCommandStatus status) {
        PlcWords words = decodeWords(wordsData);

        GripperCommand cmd = new GripperCommand();
        cmd.setTransferNo(words.getTransferNo());
        cmd.setTaskType(words.getCommandType());
        cmd.setTrayHeight(words.getTrayHeight());
        cmd.setLocationBank(words.getLocationBank());
        cmd.setLocationBay(words.getLocationBay());
        cmd.setLocationLevel(words.getLocationLevel());
        cmd.setProductId(words.getProductId());

        status.setCommand(cmd);
    }

    public GripperCommandStatus decodeCommandStatus(byte[] bitsData, byte[] wordsData) {
        GripperCommandStatus status = new GripperCommandStatus();
        decodeBits(bitsData, status);
        decodeWords(wordsData, status);
        return status;
    }

    // ==================== Word 編碼 ====================

    public int[] encodeWords(GripperCommandStatus status) {
        int[] words = new int[6 + PRODUCT_ID_WORD_COUNT];
        GripperCommand cmd = status.getCommand();
        if (cmd == null) return words;

        words[0] = cmd.getTransferNo();                                    // W0260
        words[1] = cmd.getTaskType() != null ? cmd.getTaskType().toRaw() : 0;  // W0261
        words[2] = cmd.getTrayHeight();                                    // W0262
        words[3] = cmd.getLocationBank();                                  // W0263
        words[4] = cmd.getLocationBay();                                   // W0264
        words[5] = cmd.getLocationLevel();                                 // W0265

        String productId = cmd.getProductId() != null ? cmd.getProductId() : "";
        productId = String.format("%-50s", productId);  // 補滿空白至 50 字元
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

    // ==================== Inner DTO 定義 ====================

    @Data
    public static class PlcBits {
        private boolean gripperReady;       // B0188
        private boolean removeAccountAck;   // B018A
        private boolean transferCmdReq;     // B018D
        private boolean transferCompAck;    // B018E
    }

    @Data
    public static class PlcWords {
        private int transferNo;             // W0260
        private GripperCommandType commandType; // W0261
        private int trayHeight;            // W0262
        private int locationBank;          // W0263
        private int locationBay;           // W0264
        private int locationLevel;         // W0265
        private String productId;          // W0266 ~ W027E
    }
}