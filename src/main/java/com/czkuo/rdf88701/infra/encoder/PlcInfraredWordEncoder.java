package com.czkuo.rdf88701.infra.encoder;

import com.czkuo.rdf88701.application.assembler.PlcInfraredWordCommand;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;

/**
 * 將 PlcInfraredWordCommand 編碼為 PLC Word 區域資料（W0360~W0361...）
 * - 每個 word 對應 2 bytes（小端序）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class PlcInfraredWordEncoder {

    /**
     * 編碼 Infrared 指令資料為 byte[]
     * - W0360: Measure No
     * - W0361: Task Type
     *  (若有 TrayThickness 可 W0362, 以此類推)
     */
    public byte[] encode(PlcInfraredWordCommand cmd) {
        int[] words = new int[3];

        words[0] = cmd.getMeasureNo();      // W0360
        words[1] = cmd.getTaskType();       // W0361 (e.g. 1 = MEASURE)
        words[2] = cmd.getTrayThickness();  // W0362

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
