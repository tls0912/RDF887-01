package com.czkuo.rdf88701.domain.service.strategy;

import com.czkuo.rdf88701.common.exception.BusinessException;
import com.czkuo.rdf88701.infra.entity.AutoWalkConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

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
