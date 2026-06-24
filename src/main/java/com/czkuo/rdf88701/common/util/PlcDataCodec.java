package com.czkuo.rdf88701.common.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.czkuo.rdf88701.common.util.EndianStringHelper.swapWordBytesFast;

/**
 * PlcDataCodec 提供 PLC 資料的 byte[] 與各型別資料互轉功能，
 * 純粹處理編碼與解碼，不負責通訊行為。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class PlcDataCodec {

    /**
     * PLC 資料型別定義
     */
    public enum PlcDataType {
        BIT, WORD, DWORD, FLOAT, DOUBLE, STRING
    }

    // ==================== Decode Methods (bytes -> data) ====================

    /**
     * 將 byte[] 解碼為 boolean 陣列（Bit）。
     * 每個 byte 含 8 個 bit，由低位至高位。
     * @param data 原始位元組陣列
     * @param bitCount 欲解析的 bit 數量
     * @return 對應的 boolean 陣列
     */
    public static boolean[] bytesToBits(byte[] data, int bitCount) {
        boolean[] result = new boolean[bitCount];
        for (int i = 0; i < bitCount; i++) {
            int byteIndex = i / 8;
            int bitIndex = i % 8;
            result[i] = (data[byteIndex] & (1 << bitIndex)) != 0;
        }
        return result;
    }

    /**
     * 將 byte[] 解碼為 int 陣列（Word，2 bytes 為 1 單位）。
     */
    public static int[] bytesToWords(byte[] data) {
        int[] result = new int[data.length / 2];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < result.length; i++) {
            result[i] = buffer.getShort() & 0xFFFF;
        }
        return result;
    }

    /**
     * 將 byte[] 解碼為 long 陣列（DWord / UInt32，4 bytes 為 1 單位）。
     */
    public static long[] bytesToUInt32(byte[] data) {
        long[] result = new long[data.length / 4];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < result.length; i++) {
            result[i] = buffer.getInt() & 0xFFFFFFFFL;
        }
        return result;
    }

    /**
     * 將 byte[] 解碼為 float 陣列（4 bytes 為 1 float）。
     */
    public static float[] bytesToFloats(byte[] data) {
        float[] result = new float[data.length / 4];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < result.length; i++) {
            result[i] = buffer.getFloat();
        }
        return result;
    }

    /**
     * 將 byte[] 解碼為 double 陣列（8 bytes 為 1 double）。
     */
    public static double[] bytesToDoubles(byte[] data) {
        double[] result = new double[data.length / 8];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < result.length; i++) {
            result[i] = buffer.getDouble();
        }
        return result;
    }

    /**
     * 將 byte[] 解碼為字串（依照指定 Endian，並移除尾端空白）。
     */
    public static String decodeString(byte[] data, ByteOrder order) {
        byte[] processed = (order == ByteOrder.LITTLE_ENDIAN) ? data : swapWordBytesFast(data);
        return new String(processed, StandardCharsets.US_ASCII).trim();
    }

    // ==================== Encode Methods (data -> bytes) ====================

    /**
     * 將 boolean 陣列（Bit）編碼為 byte[]（每 8 個 bit 組成 1 byte，由低位至高位）。
     */
    public static byte[] bitsToBytes(boolean[] bits) {
        int byteLength = (bits.length + 7) / 8;
        byte[] bytes = new byte[byteLength];
        for (int i = 0; i < bits.length; i++) {
            int byteIndex = i / 8;
            int bitIndex = i % 8;
            if (bits[i]) {
                bytes[byteIndex] |= (1 << bitIndex);
            }
        }
        return bytes;
    }

    /**
     * 將 int 陣列（Word）編碼為 byte[]（每個 int 佔 2 bytes）。
     */
    public static byte[] wordsToBytes(int[] words) {
        byte[] data = new byte[words.length * 2];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int word : words) {
            buffer.putShort((short) word);
        }
        return data;
    }

    /**
     * 將 long 陣列（DWord / UInt32）編碼為 byte[]（每個 long 佔 4 bytes）。
     */
    public static byte[] uint32ToBytes(long[] values) {
        byte[] data = new byte[values.length * 4];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (long value : values) {
            buffer.putInt((int) value);
        }
        return data;
    }

    /**
     * 將 float 陣列編碼為 byte[]（每個 float 佔 4 bytes）。
     */
    public static byte[] floatsToBytes(float[] floats) {
        byte[] data = new byte[floats.length * 4];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return data;
    }

    /**
     * 將 double 陣列編碼為 byte[]（每個 double 佔 8 bytes）。
     */
    public static byte[] doublesToBytes(double[] doubles) {
        byte[] data = new byte[doubles.length * 8];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (double d : doubles) {
            buffer.putDouble(d);
        }
        return data;
    }

    /**
     * 將字串編碼為 byte[]，依照指定 Endian，每個字元佔 1 byte，補空白至 wordLength 長度（1 word = 2 bytes）。
     */
    public static byte[] encodeString(String text, int wordLength, ByteOrder order) {
        byte[] raw = text.getBytes(StandardCharsets.US_ASCII);
        byte[] padded = new byte[wordLength * 2];
        Arrays.fill(padded, (byte) 0x20);
        System.arraycopy(raw, 0, padded, 0, Math.min(raw.length, padded.length));
        return (order == ByteOrder.LITTLE_ENDIAN) ? padded : swapWordBytesFast(padded);
    }
}
