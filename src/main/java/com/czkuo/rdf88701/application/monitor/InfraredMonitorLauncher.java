package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.generator.InfraredRequestGenerator;
import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.domain.repository.InfraredRepository;
import com.czkuo.rdf88701.infra.entity.Infrared;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Infrared 任務監控啟動器（防重複執行）
 * - 每組 Infrared 裝置啟動獨立排程
 * - 僅用於觸發 Request 產生策略，不處理交握邏輯
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InfraredMonitorLauncher {

    @Resource
    private MonitorPoolDispatcher dispatcher;   // 由 Spring 以 bean name 注入：@Component("GP4")、@Component("WB5")、@Component("TR2")… 等
    private final Map<String, InfraredRequestGenerator> generatorMap;
    private final InfraredRepository infraredRepository;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(8);

    /**
     * 每台 Infrared 執行狀態
     */
    private final Map<Long, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    //@PostConstruct
    public void launchAllMonitors() {
        for (Infrared infrared : infraredRepository.findAll()) {
            String name = infrared.getName(); // e.g. "Infrared#1"
            Long id = infrared.getId();

            InfraredRequestGenerator generator = generatorMap.get("IR" + id);
            if (generator == null) {
                log.warn("[InfraredMonitor] 無法找到對應策略實作：IR{}", id);
                continue;
            }

            runningFlags.put(id, new AtomicBoolean(false));

            executor.scheduleWithFixedDelay(() -> {
                AtomicBoolean flag = runningFlags.get(id);
                if (!flag.compareAndSet(false, true)) {
                    //log.debug("[InfraredMonitor] {} 上一次尚未完成，略過本輪", name);
                    return;
                }

                try {
                    dispatcher.submit("[dispatcher] Infrared#{" + id + "} MonitorLauncher", () -> {
                        infraredRepository.findById(id).ifPresent(current -> {
                            if (!Boolean.TRUE.equals(current.getEnabled())) {
                                //log.debug("[InfraredMonitor] {} 被停用，跳過此次排程", name);
                                return;
                            }

                            generator.generateRequest(id).ifPresent(reqId ->
                                    log.info("[InfraredMonitor] {} 產生新 Request：id={}", name, reqId)
                            );
                        });
                    });
                } catch (Exception e) {
                    log.error("[InfraredMonitor] {} 執行錯誤：{}", name, e.getMessage(), e);
                } finally {
                    flag.set(false); // 解鎖
                }

            }, 0, 100, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("[InfraredMonitor] 停止 Infrared 任務監控排程器...");
        executor.shutdownNow();
    }
}
