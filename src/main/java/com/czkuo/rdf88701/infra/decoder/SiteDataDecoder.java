package com.czkuo.rdf88701.infra.decoder;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.plc.state.site.SiteDeviceStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SiteDataDecoder
 * - 專門解析 PLC 傳來的 Bit/Word 資料，轉為 SiteDeviceStatus 狀態物件。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class SiteDataDecoder {

    private static final int EXPECTED_BIT_COUNT = 8;
    private static final int PRODUCT_ID_CHAR_COUNT = 50;

    /**
     * 解析 Bit 區資料（旗標）
     */
    public SiteBits decodeBits(byte[] bitsData, int siteId) {
        int bitOffset = (siteId % 2 == 1) ? 0 : 4;
        boolean[] bits = PlcDataCodec.bytesToBits(bitsData, EXPECTED_BIT_COUNT);

        SiteBits result = new SiteBits();
        result.setSiteStandby(getSafeBit(bits, bitOffset + 0));      // B0848 + offset
        result.setProductPresent(getSafeBit(bits, bitOffset + 1));   // B0849 + offset
        result.setRemoveAccountReq(getSafeBit(bits, bitOffset + 2)); // B084A + offset
        result.setPortReportReq(getSafeBit(bits, bitOffset + 3));    // B084B + offset

        return result;
    }

    /**
     * 解碼後寫入指定 SiteDeviceStatus 實例
     */
    public void decodeBits(byte[] bitsData, int siteId, SiteDeviceStatus status) {
        SiteBits bits = decodeBits(bitsData, siteId);
        status.setSiteStandby(bits.isSiteStandby());
        status.setProductPresent(bits.isProductPresent());
        status.setRemoveAccountReq(bits.isRemoveAccountReq());
        status.setPortReportPlc(bits.isPortReportReq());
    }

    /**
     * 解析 Word 區資料（設備狀態、Product ID）
     */
    public SiteWords decodeWords(byte[] wordsData) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);
        SiteWords result = new SiteWords();

        result.setDeviceStatus(getSafeWord(words, 3));  // W13E3 = index 3

        // 將 W13E4~W13FC 共 25 word 組成的 ASCII 解碼為 50 字元 Product ID
        char[] chars = new char[PRODUCT_ID_CHAR_COUNT];
        int charIndex = 0;
        for (int i = 4; i < 4 + 25; i++) {
            int word = getSafeWord(words, i);
            chars[charIndex++] = (char) (word & 0xFF);         // low byte
            chars[charIndex++] = (char) ((word >> 8) & 0xFF);  // high byte
        }
        result.setProductId(new String(chars).trim());

        return result;
    }

    public void decodeWords(byte[] wordsData, SiteDeviceStatus status) {
        SiteWords words = decodeWords(wordsData);
        status.setDeviceStatus(words.getDeviceStatus());
        status.setProductId(words.getProductId());
    }

    public SiteDeviceStatus decodeDeviceStatus(byte[] bitsData, byte[] wordsData, int siteId) {
        SiteDeviceStatus status = new SiteDeviceStatus();
        decodeBits(bitsData, siteId, status);
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
    public static class SiteBits {
        private boolean siteStandby;
        private boolean productPresent;
        private boolean removeAccountReq;
        private boolean portReportReq;
    }

    @Data
    public static class SiteWords {
        private int deviceStatus;
        private String productId;
    }
}
