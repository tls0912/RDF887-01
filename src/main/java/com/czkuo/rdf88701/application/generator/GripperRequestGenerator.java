package com.czkuo.rdf88701.application.generator;

import java.util.Optional;

/**
 * Gripper 請求產生策略接口
 * <p>
 * - 每個 Gripper 裝置根據 PLC 狀態與當前任務狀況，自主判斷是否需要產生 Request
 * - 若需產生，回傳已寫入資料庫的 Request ID；否則 Optional.empty()
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface GripperRequestGenerator {

    /**
     * 嘗試產生一筆新的 Gripper Request（若符合條件）
     *
     * @param gripperId Gripper 裝置 ID
     * @return 成功產生時，回傳新 Request 的 ID；否則 Optional.empty()
     */
    Optional<Long> generateRequest(Long gripperId);
}
