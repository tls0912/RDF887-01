package com.czkuo.rdf88701.application.service.polling.handler;

import java.time.Instant;

/**
 * PollingHandler
 * - 通用設備資料處理介面
 * - 提供 Bit / Word 資料的統一處理方法
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface PollingHandler {

    /**
     * 處理 Bit 資料
     *
     * @param deviceId      設備 ID（如 craneId、gripperId）
     * @param data          原始 Bit 區資料
     * @param startAddress  起始位址（Bit 位址）
     * @param snapshotTime  資料快照時間
     */
    void handleBitData(int deviceId, byte[] data, int startAddress, Instant snapshotTime);

    /**
     * 處理 Word 資料
     *
     * @param deviceId      設備 ID（如 craneId、gripperId）
     * @param data          原始 Word 區資料
     * @param startAddress  起始位址（Word 位址）
     * @param snapshotTime  資料快照時間
     */
    void handleWordData(int deviceId, byte[] data, int startAddress, Instant snapshotTime);
}