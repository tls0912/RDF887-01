package com.czkuo.rdf88701.domain.service.strategy;

import com.czkuo.rdf88701.infra.entity.AutoWalkConfig;

public interface AutoWalkStrategyExecutor {

    /**
     * 執行策略邏輯：根據配置產生 crane_request 或其他操作
     */
    void execute(AutoWalkConfig config);
}
