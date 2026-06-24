package com.czkuo.rdf88701.infra.decoder;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.czkuo.rdf88701.domain.plc.valueobject.WorkingBeamStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WorkingBeamDataDecoder
 * - 將切割好的 Working Beam 的 B/W 區段 byte[] 資料解析成對應結構
 * - 支援 DeviceStatus 與 RetCode 的解析
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class WorkingBeamDataDecoder {

    private static final int EXPECTED_BIT_COUNT = 8;

    /**
     * 解析 Bit 區資料
     */
    public WorkingBeamBits decodeBits(byte[] bitsData) {
        boolean[] bits = PlcDataCodec.bytesToBits(bitsData, EXPECTED_BIT_COUNT);
        WorkingBeamBits result = new WorkingBeamBits();

        result.setTransferStandby(getSafeBit(bits, 0));      // 建議根據實際位置調整
        result.setTransferCmdAck(getSafeBit(bits, 5));
        result.setTransferCompReq(getSafeBit(bits, 6));

        return result;
    }

    public void decodeBits(byte[] bitsData, WorkingBeamDeviceStatus status) {
        WorkingBeamBits bits = decodeBits(bitsData);
        status.setTransferStandby(bits.isTransferStandby());
        status.setTransferCmdAck(bits.isTransferCmdAck());
        status.setTransferCompReq(bits.isTransferCompReq());
    }

    /**
     * 解析 Word 區資料
     */
    public WorkingBeamWords decodeWords(byte[] wordsData) {
        int[] words = PlcDataCodec.bytesToWords(wordsData);
        WorkingBeamWords result = new WorkingBeamWords();

        result.setWorkingBeamStatus(WorkingBeamStatus.fromWord(getSafeWord(words, 3)));  // W1223
        result.setReturnCode(getSafeWord(words, 6));                                     // W1226

        return result;
    }

    public void decodeWords(byte[] wordsData, WorkingBeamDeviceStatus status) {
        WorkingBeamWords words = decodeWords(wordsData);
        status.setWorkingBeamStatus(words.getWorkingBeamStatus());
        status.setReturnCode(words.getReturnCode());
    }

    public WorkingBeamDeviceStatus decodeDeviceStatus(byte[] bitsData, byte[] wordsData) {
        WorkingBeamDeviceStatus status = new WorkingBeamDeviceStatus();
        decodeBits(bitsData, status);
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
    public static class WorkingBeamBits {
        private boolean transferStandby;
        private boolean transferCmdAck;
        private boolean transferCompReq;
        private boolean alarm;
    }

    @Data
    public static class WorkingBeamWords {
        private WorkingBeamStatus workingBeamStatus;
        private int returnCode;
    }
}
