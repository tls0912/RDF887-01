package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.domain.service.strategy.AutoWalkStrategyExecutor;
import com.czkuo.rdf88701.infra.entity.AutoWalkConfig;
import com.czkuo.rdf88701.infra.mapper.AutoWalkConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoWalkMonitor {

    private final AutoWalkConfigMapper configMapper;
    private final Map<String, AutoWalkStrategyExecutor> strategyExecutors;

    /**
     * 每 1 秒檢查並執行所有啟用中的 AutoWalk 策略
     */
    @Scheduled(fixedDelay = 600, initialDelay = 1000)
    public void checkAndExecuteAutoWalk() {
        List<AutoWalkConfig> enabledConfigs = configMapper.selectEnabledConfigs();
        if (enabledConfigs.isEmpty()) {
            //log.debug("[AutoWalkMonitor] 無啟用策略");
            return;
        }

        for (AutoWalkConfig config : enabledConfigs) {
            String strategyCode = config.getStrategyCode();
            if (strategyCode == null) continue;

            AutoWalkStrategyExecutor executor = strategyExecutors.get(strategyCode.trim().toUpperCase());
            if (executor != null) {
                try {
                    log.info("[AutoWalkMonitor] 執行策略：{}", strategyCode);
                    executor.execute(config);
                } catch (Exception e) {
                    log.error("[AutoWalkMonitor] 策略 '{}' 執行失敗", strategyCode, e);
                }
            } else {
                log.warn("[AutoWalkMonitor] 找不到策略實作：{}", strategyCode);
            }
        }
    }
}
