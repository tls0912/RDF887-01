package com.czkuo.rdf88701.common.util;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * EndianStringHelper 提供字串與 byte[] 間的轉換，並支援高低位（大小端）轉換。
 */
public class EndianStringHelper {

    /**
     * 將 byte[] 解碼為字串，並根據 ByteOrder 調整每 2 bytes 的位元順序。
     * @param bytes 原始位元組資料
     * @param order 位元組順序（LITTLE_ENDIAN / BIG_ENDIAN）
     * @return 解碼後字串
     */
    public static String decodeString(byte[] bytes, ByteOrder order) {
        byte[] processed = (order == ByteOrder.LITTLE_ENDIAN)
                ? bytes
                : swapWordBytesFast(bytes);
        return new String(processed, StandardCharsets.US_ASCII).trim();
    }

    /**
     * 將字串編碼為 byte[]，長度不足則補 0x20 空白，並根據 ByteOrder 調整順序。
     * @param text 字串內容
     * @param wordLength 字元長度（非 byte 數）
     * @param order 位元組順序（LITTLE_ENDIAN / BIG_ENDIAN）
     * @return 編碼後的 byte[] 資料
     */
    public static byte[] encodeString(String text, int wordLength, ByteOrder order) {
        //byte[] raw = text.getBytes(StandardCharsets.US_ASCII);
        byte[] raw = text.getBytes(Charset.forName("Big5"));
        byte[] padded = new byte[wordLength * 2];
        Arrays.fill(padded, (byte) 0x20); // 補空白
        System.arraycopy(raw, 0, padded, 0, Math.min(raw.length, padded.length));

        return (order == ByteOrder.LITTLE_ENDIAN)
                ? padded
                : swapWordBytesFast(padded);
    }

    /**
     * 每 2 bytes 為一組，交換高低位順序，回傳新的 byte[]，不改動原始陣列
     * @param bytes 欲交換的位元組陣列
     * @return 新的位元組陣列（已交換）
     */
    public static byte[] swapWordBytesFast(byte[] bytes) {
        byte[] swapped = new byte[bytes.length];
        for (int i = 0; i + 1 < bytes.length; i += 2) {
            swapped[i] = bytes[i + 1];
            swapped[i + 1] = bytes[i];
        }
        if (bytes.length % 2 != 0) {
            swapped[bytes.length - 1] = bytes[bytes.length - 1];
        }
        return swapped;
    }
}