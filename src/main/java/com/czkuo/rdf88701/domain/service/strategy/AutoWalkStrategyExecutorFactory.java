package com.czkuo.rdf88701.domain.service.strategy;

import com.czkuo.rdf88701.common.exception.BusinessException;
import com.czkuo.rdf88701.infra.entity.AutoWalkConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Component
@RequiredArgsConstructor
public class AutoWalkStrategyExecutorFactory {

    private final Map<String, AutoWalkStrategyExecutor> executorMap;

    public AutoWalkStrategyExecutor getExecutor(AutoWalkConfig config) {
        String strategyCode = config.getStrategyCode();
        AutoWalkStrategyExecutor executor = executorMap.get(strategyCode);
        if (executor == null) {
            throw new BusinessException("Unknown strategy code: " + strategyCode);
        }
        return executor;
    }
}
