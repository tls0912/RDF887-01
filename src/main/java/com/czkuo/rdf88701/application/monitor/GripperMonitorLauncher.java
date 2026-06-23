package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.generator.GripperRequestGenerator;
import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.config.plc.PlcGripperProperties;
import com.czkuo.rdf88701.domain.repository.GripperRepository;
import com.czkuo.rdf88701.infra.entity.Gripper;
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
 * Gripper 任務監控啟動器（防止重複執行）
 * - 每組 Gripper 裝置啟動獨立排程
 * - 僅用於觸發 Request 產生策略，不處理交握邏輯
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GripperMonitorLauncher {

    private final Map<String, GripperRequestGenerator> generatorMap;
    private final GripperRepository gripperRepository;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(8);

    /**
     * 每台 Gripper 執行狀態旗標
     */
    private final Map<Long, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    /**
     * 交給 DualGroupOrchestrator 的
     */
    private static final Set<Long> EXCLUDED_IDS = Set.of(2L, 4L, 5L);
    @Resource
    private MonitorPoolDispatcher dispatcher;

    @PostConstruct
    public void launchAllMonitors() {
        for (Gripper gripper : gripperRepository.findAll()) {
            String name = gripper.getName(); // e.g. "Gripper#2"
            Long id = gripper.getId();

            GripperRequestGenerator generator = generatorMap.get("GP" + id);
            if (generator == null) {
                log.warn("[GripperMonitor] 無法找到對應策略實作：GP{}", id);
                continue;
            }

            if (EXCLUDED_IDS.contains(id)) {
                log.info("[GripperMonitor] skip GP{} (由 Orchestrator 管控)", id);
                continue;
            }

            runningFlags.put(id, new AtomicBoolean(false));

            executor.scheduleWithFixedDelay(() -> {
                AtomicBoolean flag = runningFlags.get(id);
                if (!flag.compareAndSet(false, true)) {
                    //log.debug("[GripperMonitor] {} 上一次尚未完成，略過本輪", name);
                    return;
                }

                try {
                    dispatcher.submit("[dispatcher] Gripper#{" + id + "} MonitorLauncher", () -> {
                        gripperRepository.findById(id).ifPresent(current -> {
                            if (!Boolean.TRUE.equals(current.getEnabled())) {
                                //log.debug("[GripperMonitor] {} 被停用，跳過此次排程", name);
                                return;
                            }

                            generator.generateRequest(id).ifPresent(reqId ->
                                    log.info("[GripperMonitor] {} 產生新 Request：id={}", name, reqId)
                            );
                        });
                    });
                } catch (Exception e) {
                    log.error("[GripperMonitor] {} 執行錯誤：{}", name, e.getMessage(), e);
                } finally {
                    flag.set(false); // 解鎖
                }

            }, 0, 50, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("[GripperMonitor] 停止 Gripper 任務監控排程器...");
        executor.shutdownNow();
    }
}
