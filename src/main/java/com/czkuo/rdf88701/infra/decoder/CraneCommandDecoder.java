package com.czkuo.rdf88701.infra.decoder;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.plc.command.CraneCommand;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneCommandStatus;
import com.czkuo.rdf88701.domain.plc.valueobject.FromCraneCommandType;
import com.czkuo.rdf88701.domain.plc.valueobject.ToCraneCommandType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * CraneCommandDecoder
 * - 專門解析與組裝 PC 控制區段（Bit: B0030+ / Word: W0050+）
 * - 將 PLC Byte[] ↔ CranePlcCommandStatus 雙向轉換
 * - 支援 Transfer Type、CST ID、Location 等語意
 */
@Slf4j
@Component
public class CraneCommandDecoder {

    private static final int EXPECTED_BIT_COUNT = 32;

    // ==================== Bit 區解碼 ====================

    public PlcBits decodeBits(byte[] bitsData) {
        boolean[] bits = PlcDataCodec.bytesToBits(bitsData, EXPECTED_BIT_COUNT);
        PlcBits result = new PlcBits();

        result.setTransferReady(getSafeBit(bits, 0));
        result.setFromTransferCmdReq(getSafeBit(bits, 1));
        result.setFromTransferCompAck(getSafeBit(bits, 2));
        result.setToTransferCmdReq(getSafeBit(bits, 3));
        result.setToTransferCompAck(getSafeBit(bits, 4));
        result.setHomeReturnRequest(getSafeBit(bits, 7));
        result.setRemoveAccountAck(getSafeBit(bits, 8));

        return result;
    }

    public void decodeBits(byte[] bitsData, CraneCommandStatus status) {
        PlcBits bits = decodeBits(bitsData);
        status.setTransferReady(bits.isTransferReady());
        status.setFromTransferCmdReq(bits.isFromTransferCmdReq());
        status.setFromTransferCompAck(bits.isFromTransferCompAck());
        status.setToTransferCmdReq(bits.isToTransferCmdReq());
        status.setToTransferCompAck(bits.isToTransferCompAck());
        status.setHomeReturnRequest(bits.isHomeReturnRequest());
        status.setRemoveAccountAck(bits.isRemoveAccountAck());
    }

    public byte[] encodeBits(CraneCommandStatus status) {
        boolean[] bits = new boolean[EXPECTED_BIT_COUNT];
        bits[0] = status.isTransferReady();
        bits[1] = status.isFromTransferCmdReq();
        bits[2] = status.isFromTransferCompAck();
        bits[3] = status.isToTransferCmdReq();
        bits[4] = status.isToTransferCompAck();
        bits[7] = status.isHomeReturnRequest();
        bits[8] = status.isRemoveAccountAck();
        return PlcDataCodec.bitsToBytes(bits);
    }

    // ==================== Word 區解碼 ====================

    public PlcWords decodeWords(byte[] wordsData) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);
        PlcWords result = new PlcWords();

        result.setFromCraneCommandType(FromCraneCommandType.fromWord(getSafeWord(words, 0)));
        result.setFromTransferNo(getSafeWord(words, 1));
        result.setFromCstId(decodeCstId(words, 2));

        result.setFromLocationType(getSafeWord(words, 27));
        result.setFromLocationBank(getSafeWord(words, 28));
        result.setFromLocationBay(getSafeWord(words, 29));
        result.setFromLocationLv(getSafeWord(words, 30));

        result.setToCraneCommandType(ToCraneCommandType.fromWord(getSafeWord(words, 31)));
        result.setToTransferNo(getSafeWord(words, 32));
        result.setToCstId(decodeCstId(words, 33));

        result.setToLocationType(getSafeWord(words, 58));
        result.setToLocationBank(getSafeWord(words, 59));
        result.setToLocationBay(getSafeWord(words, 60));
        result.setToLocationLv(getSafeWord(words, 61));

        return result;
    }

    public void decodeWords(byte[] wordsData, CraneCommandStatus status) {
        PlcWords words = decodeWords(wordsData);

        CraneCommand cmd = new CraneCommand();
        cmd.setFromCraneCommandType(words.getFromCraneCommandType());
        cmd.setFromTransferNo(words.getFromTransferNo());
        cmd.setFromCstId(words.getFromCstId());
        cmd.setFromLocationType(words.getFromLocationType());
        cmd.setFromLocationBank(words.getFromLocationBank());
        cmd.setFromLocationBay(words.getFromLocationBay());
        cmd.setFromLocationLv(words.getFromLocationLv());

        cmd.setToCraneCommandType(words.getToCraneCommandType());
        cmd.setToTransferNo(words.getToTransferNo());
        cmd.setToCstId(words.getToCstId());
        cmd.setToLocationType(words.getToLocationType());
        cmd.setToLocationBank(words.getToLocationBank());
        cmd.setToLocationBay(words.getToLocationBay());
        cmd.setToLocationLv(words.getToLocationLv());

        status.setCommand(cmd);
    }

    public CraneCommandStatus decodeCommandStatus(byte[] bitsData, byte[] wordsData) {
        CraneCommandStatus status = new CraneCommandStatus();
        decodeBits(bitsData, status);
        decodeWords(wordsData, status);
        return status;
    }

    // ==================== Word 區編碼 ====================

    public int[] encodeWords(CraneCommandStatus status) {
        int[] words = new int[64];
        CraneCommand cmd = status.getCommand();
        if (cmd == null) return words;

        words[0] = cmd.getFromCraneCommandType() != null ? cmd.getFromCraneCommandType().toRaw() : 0;
        words[1] = cmd.getFromTransferNo();
        encodeCstId(cmd.getFromCstId(), words, 2);

        words[28] = cmd.getFromLocationType();
        words[29] = cmd.getFromLocationBank();
        words[30] = cmd.getFromLocationBay();
        words[31] = cmd.getFromLocationLv();

        words[32] = cmd.getToCraneCommandType() != null ? cmd.getToCraneCommandType().toRaw() : 0;
        words[33] = cmd.getToTransferNo();
        encodeCstId(cmd.getToCstId(), words, 34);

        words[60] = cmd.getToLocationType();
        words[61] = cmd.getToLocationBank();
        words[62] = cmd.getToLocationBay();
        words[63] = cmd.getToLocationLv();

        return words;
    }

    // ==================== CST ID 處理 ====================

    public String decodeCstId(int[] words, int offset) {
        int[] cstWords = Arrays.copyOfRange(words, offset, offset + 25);
        byte[] bytes = PlcDataCodec.wordsToBytes(cstWords);
        return PlcDataCodec.decodeString(bytes, ByteOrder.LITTLE_ENDIAN).trim();
    }

    private void encodeCstId(String cstId, int[] target, int offset) {
        byte[] cstBytes = PlcDataCodec.encodeString(cstId, 50, ByteOrder.LITTLE_ENDIAN);
        int[] cstWords = PlcDataCodec.bytesToWords(cstBytes);
        System.arraycopy(cstWords, 0, target, offset, Math.min(cstWords.length, 25));
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
        private boolean transferReady;
        private boolean fromTransferCmdReq;
        private boolean fromTransferCompAck;
        private boolean toTransferCmdReq;
        private boolean toTransferCompAck;
        private boolean homeReturnRequest;
        private boolean removeAccountAck;
    }

    @Data
    public static class PlcWords {
        private FromCraneCommandType fromCraneCommandType;
        private int fromTransferNo;
        private String fromCstId;
        private int fromLocationType;
        private int fromLocationBank;
        private int fromLocationBay;
        private int fromLocationLv;

        private ToCraneCommandType toCraneCommandType;
        private int toTransferNo;
        private String toCstId;
        private int toLocationType;
        private int toLocationBank;
        private int toLocationBay;
        private int toLocationLv;
    }
}
