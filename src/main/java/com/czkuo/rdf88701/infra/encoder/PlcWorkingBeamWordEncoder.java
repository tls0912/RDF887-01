package com.czkuo.rdf88701.infra.encoder;

import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcWorkingBeamWordCommand;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;

/**
 * 將 PlcWorkingBeamWordCommand 編碼為 PLC Word 區域資料（W0220~W0222）
 * - 每個 word 對應 2 bytes（小端序）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class PlcWorkingBeamWordEncoder {

    /**
     * 編碼 Working Beam 指令資料為 byte[]
     * - W0220: Transfer No
     * - W0221: Transfer Type
     * - W0222: Direction
     */
    public byte[] encode(PlcWorkingBeamWordCommand cmd) {
        int[] words = new int[3];

        words[0] = cmd.getTransferNo();     // W0220
        words[1] = cmd.getTransferType();   // W0221 (e.g. 1 = Move)
        words[2] = cmd.getDirection();      // W0222 (e.g. 1 = IN, 2 = OUT)

        return convertWordsToBytes(words);
    }

    /**
     * 將 int[] word 陣列轉為 byte[]（每 word 2 bytes，小端序）
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