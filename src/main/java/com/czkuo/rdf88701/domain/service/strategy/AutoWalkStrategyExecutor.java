package com.czkuo.rdf88701.domain.service.strategy;

import com.czkuo.rdf88701.infra.entity.AutoWalkConfig;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface AutoWalkStrategyExecutor {

    /**
     * 執行策略邏輯：根據配置產生 crane_request 或其他操作
     */
    void execute(AutoWalkConfig config);
}
