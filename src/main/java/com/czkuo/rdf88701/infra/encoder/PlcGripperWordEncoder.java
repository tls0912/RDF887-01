package com.czkuo.rdf88701.infra.encoder;

import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcGripperWordCommand;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * PlcGripperWordEncoder
 * - 將 PlcGripperWordCommand 轉換為 PLC Word 區段 byte[]
 * - 對應地址區段：W0200 ~ W021F
 */
@Component
public class PlcGripperWordEncoder {

    /**
     * 將 PlcGripperWordCommand 編碼為 Word 陣列後轉為 byte[]
     * 對應區間：W0200 ~ W021F（共 32 Word）
     */
    public byte[] encode(PlcGripperWordCommand cmd) {
        int[] words = new int[32];

        // W0260 = Gripper No
        words[0] = cmd.getTransferNo();

        // W0261 = Gripper Type（低 4 位元）
        //words[1] = cmd.getCommandType();
        words[1] = cmd.getCommandType() + (cmd.getTrayQuantity() << 8);

        // W0262 = Tray Height
        words[2] = cmd.getTrayHeight();

        // W0265 = Location Level
        words[5] = cmd.getLocationLevel();

        // W0266 ~ W027E = Product ID（最大 50 字元 ASCII）
        int[] productIdWords = encodeAsciiToWords(cmd.getProductId(), 25);
        System.arraycopy(productIdWords, 0, words, 6, 25);

        // W027F = Spare Word
        words[31] = cmd.getSpareWord() != null ? cmd.getSpareWord() : 0;

        return convertWordsToBytes(words);
    }

    /**
     * 將 ASCII 字串轉為 Word 陣列（每兩字元為一個 Word）
     */
    private int[] encodeAsciiToWords(String text, int wordCount) {
        byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.US_ASCII);
        int[] words = new int[wordCount];
        Arrays.fill(words, 0);

        for (int i = 0; i < wordCount; i++) {
            int low = (i * 2 < bytes.length) ? (bytes[i * 2] & 0xFF) : 0;
            int high = (i * 2 + 1 < bytes.length) ? (bytes[i * 2 + 1] & 0xFF) : 0;
            words[i] = (high << 8) | low;
        }
        return words;
    }

    /**
     * 將 int[] word 陣列轉為 byte[]（每 word 2 bytes，低位在前，高位在後）
     */
    private byte[] convertWordsToBytes(int[] words) {
        ByteBuffer buffer = ByteBuffer.allocate(words.length * 2);
        for (int word : words) {
            buffer.put((byte) (word & 0xFF));        // Low byte
            buffer.put((byte) ((word >> 8) & 0xFF)); // High byte
        }
        return buffer.array();
    }
}
