package com.czkuo.rdf88701.infra.decoder;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.plc.state.site.SiteCommandStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SiteCommandDecoder
 * - 專門負責解析 PC 寫入至 Site 的控制區段（Bit / Word）
 * - 用於從 PLC 讀取 byte[]，轉成 SiteCommandStatus
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class SiteCommandDecoder {

    private static final int EXPECTED_BIT_COUNT = 4;
    private static final int PRODUCT_ID_WORD_START = 6;   // W03EC ~ W03FE
    private static final int PRODUCT_ID_WORD_COUNT = 25;  // 50 ASCII 字元 = 25 Word

    // ==================== Bit 區解碼 ====================

    public PlcBits decodeBits(byte[] bitsData) {
        boolean[] bits = PlcDataCodec.bytesToBits(bitsData, EXPECTED_BIT_COUNT);
        PlcBits result = new PlcBits();
        result.setSiteReady(getSafeBit(bits, 0));
        result.setRemoveAccountAck(getSafeBit(bits, 2));
        result.setPortReportAck(getSafeBit(bits, 3));
        return result;
    }

    public void decodeBits(byte[] bitsData, SiteCommandStatus status) {
        PlcBits bits = decodeBits(bitsData);
        status.setSiteReady(bits.isSiteReady());
        status.setRemoveAccountAck(bits.isRemoveAccountAck());
        status.setPortReportPc(bits.isPortReportAck());
    }

    public byte[] encodeBits(SiteCommandStatus status) {
        boolean[] bits = new boolean[EXPECTED_BIT_COUNT];
        bits[0] = status.isSiteReady();
        bits[2] = status.isRemoveAccountAck();
        bits[3] = status.isPortReportPc();
        return PlcDataCodec.bitsToBytes(bits);
    }

    // ==================== Word 區解碼 ====================

    public PlcWords decodeWords(byte[] wordsData) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);
        PlcWords result = new PlcWords();

        // Product ID 解碼（W03EC ~ W03FE）
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

    public void decodeWords(byte[] wordsData, SiteCommandStatus status) {
        PlcWords words = decodeWords(wordsData);
        status.setProductId(words.getProductId());
    }

    public SiteCommandStatus decodeCommandStatus(byte[] bitsData, byte[] wordsData) {
        SiteCommandStatus status = new SiteCommandStatus();
        decodeBits(bitsData, status);
        decodeWords(wordsData, status);
        return status;
    }

    // ==================== Word 區編碼 ====================

    public int[] encodeWords(SiteCommandStatus status) {
        int[] words = new int[PRODUCT_ID_WORD_START + PRODUCT_ID_WORD_COUNT];
        String productId = status.getProductId() != null ? status.getProductId() : "";
        productId = String.format("%-50s", productId);  // 補滿 50 字元

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
        private boolean siteReady;
        private boolean removeAccountAck;
        private boolean portReportAck;
    }

    @Data
    public static class PlcWords {
        private String productId;
    }
}
