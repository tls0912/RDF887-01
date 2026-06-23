package com.czkuo.rdf88701.infra.encoder;

import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcTransferWordCommand;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * PlcTransferWordEncoder
 * - 將 PlcTransferWordCommand 轉換為 PLC Word 區段 byte[]
 * - 對應地址區段：W0100 ~ W011E
 */
@Component
public class PlcTransferWordEncoder {

    /**
     * 將 PlcTransferWordCommand 編碼為 Word 陣列後轉為 byte[]
     * 對應區間：W0100 ~ W011E（共 31 Word）
     */
    public byte[] encode(PlcTransferWordCommand cmd) {
        int[] words = new int[31];

        // W0100 = Transfer No
        words[0] = cmd.getTransferNo();

        // W0101 = Transfer Type（低4位元）
        words[1] = cmd.getTransferType();

        // W0103~W0105 = Bank / Bay / Level
        words[3] = cmd.getLocationBank();
        words[4] = cmd.getLocationBay();
        words[5] = cmd.getLocationLevel();

        // W0106~W011E = Product ID（25 Words, 最多 50 字元 ASCII）
        int[] productIdWords = encodeAsciiToWords(cmd.getProductId(), 25);
        System.arraycopy(productIdWords, 0, words, 6, 25);

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
