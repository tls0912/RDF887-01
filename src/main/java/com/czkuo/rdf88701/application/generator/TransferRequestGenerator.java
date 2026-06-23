package com.czkuo.rdf88701.application.generator;

import java.util.Optional;

/**
 * Transfer 請求產生策略接口
 * <p>
 * - 每個 Transfer 裝置根據 PLC 狀態與當前任務狀況，自主判斷是否需要產生 Request
 * - 若需產生，回傳已寫入資料庫的 Request ID；否則 Optional.empty()
 */
public interface TransferRequestGenerator {

    /**
     * 嘗試產生一筆新的 Transfer Request（若符合條件）
     *
     * @param transferId Transfer 裝置 ID
     * @return 成功產生時，回傳新 Request 的 ID；否則 Optional.empty()
     */
    Optional<Long> generateRequest(Long transferId);
}
