package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.generator.TransferRequestGenerator;
import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.domain.repository.TransferRepository;
import com.czkuo.rdf88701.infra.entity.Transfer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Transfer 任務監控啟動器（防重複執行）
 * - 每組 Transfer 裝置啟動獨立排程
 * - 僅用於觸發 Request 產生策略，不處理交握邏輯
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferMonitorLauncher {

    @Resource
    private MonitorPoolDispatcher dispatcher;   // 由 Spring 以 bean name 注入：@Component("GP4")、@Component("WB5")、@Component("TR2")… 等
    private final Map<String, TransferRequestGenerator> generatorMap;
    private final TransferRepository transferRepository;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(8);

    /**
     * 每台 Transfer 執行狀態
     */
    private final Map<Long, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    /**
     * 交給 DualGroupOrchestrator 的
     */
    private static final Set<Long> EXCLUDED_IDS = Set.of(2L, 4L, 5L);

    @PostConstruct
    public void launchAllMonitors() {
        for (Transfer transfer : transferRepository.findAll()) {
            String name = transfer.getName(); // e.g. "Transfer#1"
            Long id = transfer.getId();

            TransferRequestGenerator generator = generatorMap.get("TR" + id);
            if (generator == null) {
                log.warn("[TransferMonitor] 無法找到對應策略實作：TR{}", id);
                continue;
            }

            if (EXCLUDED_IDS.contains(id)) {
                log.info("[TransferMonitor] skip TR{} (由 Orchestrator 管控)", id);
                continue;
            }

            runningFlags.put(id, new AtomicBoolean(false));

            executor.scheduleWithFixedDelay(() -> {
                AtomicBoolean flag = runningFlags.get(id);
                if (!flag.compareAndSet(false, true)) {
                    //log.debug("[TransferMonitor] {} 上一次尚未完成，略過本輪", name);
                    return;
                }

                try {
                    dispatcher.submit("[dispatcher] Transfer#{" + id + "} MonitorLauncher", () -> {
                        transferRepository.findById(id).ifPresent(current -> {
                            if (!Boolean.TRUE.equals(current.getEnabled())) {
                                //log.debug("[TransferMonitor] {} 被停用，跳過此次排程", name);
                                return;
                            }

                            generator.generateRequest(id).ifPresent(reqId ->
                                    log.info("[TransferMonitor] {} 產生新 Request：id={}", name, reqId)
                            );
                        });
                    });
                } catch (Exception e) {
                    log.error("[TransferMonitor] {} 執行錯誤：{}", name, e.getMessage(), e);
                } finally {
                    flag.set(false); // 解鎖
                }

            }, 0, 100, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("[TransferMonitor] 停止 Transfer 任務監控排程器...");
        executor.shutdownNow();
    }
}
