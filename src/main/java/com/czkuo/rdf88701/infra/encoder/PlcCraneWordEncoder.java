package com.czkuo.rdf88701.infra.encoder;

import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcCraneWordCommand;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 將 PlcCraneWordCommand 轉為 PLC Word array（byte[]，每 word 2 bytes）
 * 支援 From / To 區段（W0050~W008D）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class PlcCraneWordEncoder {

    /**
     * 編碼 From 區段（對應 PLC W0050~W006E）
     */
    public byte[] encodeFromSection(PlcCraneWordCommand cmd) {
        int[] words = new int[31];

        // W0050 = 0bCT
        words[0] = ((cmd.getFromBcrFlag() & 0x01) << 8)
                | ((cmd.getFromCstType() & 0x0F) << 4)
                | (cmd.getFromCommandType() & 0x0F);

        words[1] = cmd.getFromTransferNo();           // W0051

        int[] cstWords = encodeAsciiToWords(cmd.getFromCstId(), 25); // W0052~W006A
        System.arraycopy(cstWords, 0, words, 2, 25);

        words[27] = cmd.getFromLocationType();        // W006B
        words[28] = cmd.getFromBank();                // W006C
        words[29] = cmd.getFromBay();                 // W006D
        words[30] = cmd.getFromLevel();               // W006E

        return convertWordsToBytes(words);
    }

    /**
     * 編碼 To 區段（對應 PLC W006F~W008D）
     */
    public byte[] encodeToSection(PlcCraneWordCommand cmd) {
        int[] words = new int[31];

        // W006F = 00CT
        words[0] = ((cmd.getToCstType() & 0x0F) << 4)
                | (cmd.getToCommandType() & 0x0F);

        words[1] = cmd.getToTransferNo();             // W0070

        int[] cstWords = encodeAsciiToWords(cmd.getToCstId(), 25); // W0071~W0089
        System.arraycopy(cstWords, 0, words, 2, 25);

        words[27] = cmd.getToLocationType();          // W008A
        words[28] = cmd.getToBank();                  // W008B
        words[29] = cmd.getToBay();                   // W008C
        words[30] = cmd.getToLevel();                 // W008D

        return convertWordsToBytes(words);
    }

    /**
     * 將 ASCII 字串編碼為 Word 陣列（每 2 個字元為一個 word）
     * 不足則補 0，長度上限為 wordCount 個 word
     */
    private int[] encodeAsciiToWords(String text, int wordCount) {
        byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.US_ASCII);
        int[] words = new int[wordCount];
        Arrays.fill(words, 0);

//        for (int i = 0; i < wordCount; i++) {
//            int high = (i * 2 < bytes.length) ? (bytes[i * 2] & 0xFF) : 0;
//            int low = (i * 2 + 1 < bytes.length) ? (bytes[i * 2 + 1] & 0xFF) : 0;
//            words[i] = (high << 8) | low;
//        }

        for (int i = 0; i < wordCount; i++) {
            int low = (i * 2 < bytes.length) ? (bytes[i * 2] & 0xFF) : 0;
            int high = (i * 2 + 1 < bytes.length) ? (bytes[i * 2 + 1] & 0xFF) : 0;
            words[i] = (high << 8) | low;
        }

        return words;
    }

    /**
     * 將 int[] word 陣列轉為 byte[]（每 word 2 bytes，高位在前）
     */
    private byte[] convertWordsToBytes(int[] words) {
        ByteBuffer buffer = ByteBuffer.allocate(words.length * 2);
        for (int word : words) {
            buffer.put((byte) (word & 0xFF));        // Low byte first
            buffer.put((byte) ((word >> 8) & 0xFF)); // High byte second
        }
        return buffer.array();
    }
}
