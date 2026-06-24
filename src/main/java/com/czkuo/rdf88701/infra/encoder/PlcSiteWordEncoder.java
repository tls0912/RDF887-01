package com.czkuo.rdf88701.infra.encoder;

import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcSiteWordCommand;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * PlcSiteWordEncoder
 * - 將 Site Word 指令打包為 byte[]（比照 Transfer 的編碼方式）
 * - 區段：W03E0 ~ W03FF（共 32 Words）
 *   - W03E6 ~ W03FE = Product ID（25 Words = 最多 50 ASCII 字元）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class PlcSiteWordEncoder {

    public static final int TOTAL_WORDS = 32;        // W03E0..W03FF
    public static final int PRODUCT_ID_WORD_START = 6;   // W03E6
    public static final int PRODUCT_ID_WORD_COUNT = 25;  // W03E6..W03FE

    /**
     * 完整打包整段（含前後 Spare 區）
     */
    public byte[] encode(PlcSiteWordCommand cmd) {
        int[] words = new int[TOTAL_WORDS];

        // ProductId → 25 words（低位=第1字、高位=第2字）
        int[] pid = encodeAsciiToWords(cmd.getProductId(), PRODUCT_ID_WORD_COUNT);
        System.arraycopy(pid, 0, words, PRODUCT_ID_WORD_START, PRODUCT_ID_WORD_COUNT);

        return convertWordsToBytes(words);
    }

    /**
     * 只打包 ASCII50（25 words = 50 bytes）
     * - 搭配 writer 從 base + PRODUCT_ID_WORD_START 寫入
     */
    public byte[] encodeAscii50Bytes(String productId) {
        int[] pid = encodeAsciiToWords(productId, PRODUCT_ID_WORD_COUNT);
        return convertWordsToBytes(pid);
    }

    /** 將 ASCII 字串轉為 Word 陣列：每 2 字元 → 1 Word（low = 第1字, high = 第2字） */
    private int[] encodeAsciiToWords(String text, int wordCount) {
        byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.US_ASCII);
        int[] words = new int[wordCount];
        Arrays.fill(words, 0);

        for (int i = 0; i < wordCount; i++) {
            int low  = (i * 2 < bytes.length)      ? (bytes[i * 2] & 0xFF)     : 0;
            int high = (i * 2 + 1 < bytes.length)  ? (bytes[i * 2 + 1] & 0xFF) : 0;
            words[i] = (high << 8) | low;
        }
        return words;
    }

    /** 將 word[] 轉為 byte[]（little-endian：low byte → high byte） */
    private byte[] convertWordsToBytes(int[] words) {
        ByteBuffer buffer = ByteBuffer.allocate(words.length * 2);
        for (int word : words) {
            buffer.put((byte) (word & 0xFF));        // low
            buffer.put((byte) ((word >> 8) & 0xFF)); // high
        }
        return buffer.array();
    }

    /** log sample */
    public static String sample50(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ').replace('\r', ' ');
        return s.length() <= 50 ? s : s.substring(0, 50) + "…";
    }
}
