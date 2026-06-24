package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.generator.WorkingBeamRequestGenerator;
import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.domain.repository.WorkingBeamRepository;
import com.czkuo.rdf88701.infra.entity.WorkingBeam;
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
 * WorkingBeam 任務監控啟動器（防重複執行版）
 * - 每組 WorkingBeam 啟動獨立的監控排程
 * - 僅用於觸發 Request 產生策略，不處理交握邏輯
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkingBeamMonitorLauncher {

    @Resource
    private MonitorPoolDispatcher dispatcher;   // 由 Spring 以 bean name 注入：@Component("GP4")、@Component("WB5")、@Component("TR2")… 等
    private final Map<String, WorkingBeamRequestGenerator> generatorMap;
    private final WorkingBeamRepository workingBeamRepository;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(8);

    /**
     * 控制每支 WorkingBeam 是否正在執行中
     */
    private final Map<Long, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    /**
     * 交給 DualGroupOrchestrator 的
     */
    private static final Set<Long> EXCLUDED_IDS = Set.of(1L, 5L, 6L, 7L,8L);

    @PostConstruct
    public void launchAllMonitors() {
        for (WorkingBeam beam : workingBeamRepository.findAll()) {
            String name = beam.getName();   // e.g. "WorkingBeam#1"
            Long id = beam.getId();

            WorkingBeamRequestGenerator generator = generatorMap.get("WB" + id);
            if (generator == null) {
                log.warn("[WorkingBeamMonitor] 無法找到對應策略實作：WB{}", id);
                continue;
            }

            if (EXCLUDED_IDS.contains(id)) {
                log.info("[WorkingBeamMonitor] skip WB{} (由 Orchestrator 管控)", id);
                continue;
            }

            runningFlags.put(id, new AtomicBoolean(false));

            executor.scheduleWithFixedDelay(() -> {
                AtomicBoolean flag = runningFlags.get(id);
                if (!flag.compareAndSet(false, true)) {
                    //log.debug("[WorkingBeamMonitor] {} 上一次尚未完成，略過本輪", name);
                    return;
                }

                try {
                    dispatcher.submit("[dispatcher] WorkingBeam#{" + id + "} MonitorLauncher", () -> {
                        workingBeamRepository.findById(id).ifPresent(current -> {
                            if (!Boolean.TRUE.equals(current.getEnabled())) {
                                //log.debug("[WorkingBeamMonitor] {} 已被停用，跳過此次排程", name);
                                return;
                            }

                            generator.generateRequest(id).ifPresent(reqId ->
                                    log.info("[WorkingBeamMonitor] {} 產生新 Request：id={}", name, reqId)
                            );
                        });
                    });
                } catch (Exception e) {
                    log.error("[WorkingBeamMonitor] {} 執行錯誤：{}", name, e.getMessage(), e);
                } finally {
                    flag.set(false); // 解鎖
                }

            }, 0, 50, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("[Monitor] 停止 WorkingBeam 任務監控排程器...");
        executor.shutdownNow();
    }
}