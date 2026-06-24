package com.czkuo.rdf88701.common.util;

/**
 * PLC 通訊地址與單位轉換工具
 * <p>
 * 用於將元件數量換算成實際通訊所需的 Byte 數量，或從 Byte 數量推回元件數量
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class PlcAddressUtils {

    private PlcAddressUtils() {
        // 工具類禁止實例化
    }

    /**
     * 根據完整地址（如 "B0000"）與元件數量，計算需要讀取的實際 Byte 數量
     *
     * @param address        PLC 位址（如 B0000, W1000）
     * @param componentCount 元件數量
     * @return 實際需要讀取的 Byte 數量
     */
    public static int calculateByteLength(String address, int componentCount) {
        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("PLC address must not be null or empty");
        }
        String prefix = address.substring(0, 1).toUpperCase();
        return calculateByteLengthByType(prefix, componentCount);
    }

    /**
     * 計算指定元件起始位置在 fullData 中的 byte offset
     *
     * @param type        區段類型（B, W）
     * @param componentOffset  距離 pollStart 的元件偏移數（以元件為單位，而不是 byte）
     * @return byte offset
     */
    public static int calculateByteOffsetByType(String type, int componentOffset) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("PLC area type must not be null or empty");
        }
        return switch (type.toUpperCase()) {
            case "B", "M", "X", "Y" -> componentOffset / 8;   // bit 打包，每 8 個佔 1 byte
            case "W", "D", "R", "Z" -> componentOffset * 2;   // word 類型，每 1 個佔 2 bytes
            default -> throw new IllegalArgumentException("Unsupported PLC area type: " + type);
        };
    }

    /**
     * 根據類型（如 "B", "W"）與元件數量，計算需要讀取的實際 Byte 數量
     * <p>
     * - Bit 類型（B, M, X, Y）：(componentCount + 7) / 8
     * - Word 類型（W, D, R, Z）：componentCount * 2
     *
     * @param type           區段類型（B, W, D 等）
     * @param componentCount 元件數量
     * @return 實際需要讀取的 Byte 數量
     */
    public static int calculateByteLengthByType(String type, int componentCount) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("PLC area type must not be null or empty");
        }
        return switch (type.toUpperCase()) {
            case "B", "M", "X", "Y" -> (componentCount + 7) / 8;  // Bit類型要打包
            case "W", "D", "R", "Z" -> componentCount * 2;       // Word類型2bytes
            default -> throw new IllegalArgumentException("Unsupported PLC area type: " + type);
        };
    }

    /**
     * 根據類型（如 "B", "W"）與實際讀取的 Byte 數量，推回元件數量
     * <p>
     * - Bit 類型（B, M, X, Y）：byteLength * 8
     * - Word 類型（W, D, R, Z）：byteLength / 2
     *
     * @param type        區段類型（B, W, D 等）
     * @param byteLength  實際讀取的 Byte 數量
     * @return 換算回的元件數量
     */
    public static int calculateComponentCountByType(String type, int byteLength) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("PLC area type must not be null or empty");
        }
        return switch (type.toUpperCase()) {
            case "B", "M", "X", "Y" -> byteLength * 8;
            case "W", "D", "R", "Z" -> byteLength / 2;
            default -> throw new IllegalArgumentException("Unsupported PLC area type: " + type);
        };
    }

    /**
     * 取得指定地址的單位大小（Byte）
     * <p>
     * - B, M, X, Y 類型每個元件視為 1 Byte
     * - W, D, R, Z 類型每個元件視為 2 Bytes
     *
     * @param address PLC 位址（如 B0000, W1000）
     * @return 單位大小（Bytes）
     */
    private static int getUnitSizeInBytesFromAddress(String address) {
        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("PLC address must not be null or empty");
        }
        String prefix = address.substring(0, 1).toUpperCase();
        return getUnitSizeInBytesFromType(prefix);
    }

    /**
     * 取得指定類型的單位大小（Byte）
     *
     * @param type 區段類型（B, W, D 等）
     * @return 單位大小（Bytes）
     */
    private static int getUnitSizeInBytesFromType(String type) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("PLC area type must not be null or empty");
        }
        return switch (type.toUpperCase()) {
            case "B", "M", "X", "Y" -> 1;
            case "W", "D", "R", "Z" -> 2;
            default -> throw new IllegalArgumentException("Unsupported PLC area type: " + type);
        };
    }

    /**
     * 根據類型（如 "B", "W"）取得單一元件的大小（Bytes）
     *
     * @param type 區段類型（B, W, D 等）
     * @return 單位大小（Bytes）
     */
    public static int getUnitSizeInBytesByType(String type) {
        return getUnitSizeInBytesFromType(type);
    }

    /**
     * 將十進位 Address 轉為標準 PLC 十六進位格式（例如 "0x0600"）
     *
     * @param address 十進位 Address
     * @return 轉換後字串，例如 0x0600
     */
    public static String formatAddressHex(int address) {
        return String.format("0x%04X", address);
    }

    /**
     * 將十進位 Address 轉成純16進位字串（不帶0x）
     * 例如 768 -> "0300"
     *
     * @param address 十進位 Address
     * @return 轉換後字串，例如 "0300"
     */
    public static String formatAddressHexWithout0x(int address) {
        return String.format("%04X", address);
    }
}
