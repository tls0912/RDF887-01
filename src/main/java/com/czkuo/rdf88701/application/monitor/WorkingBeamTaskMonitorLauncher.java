package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.config.plc.PlcWorkingBeamRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WorkingBeam 任務監控啟動器
 * - 每台 WorkingBeam 啟動獨立任務監控排程（每秒執行一次）
 * - 若該設備上一次任務尚未完成，則本輪排程會略過（避免併發）
 * - 每隻 WorkingBeam 皆以 beamName 作為獨立控制
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkingBeamTaskMonitorLauncher {

    private final PlcWorkingBeamRegistry registry;
    private final WorkingBeamTaskMonitorPerDevice monitorPerDevice;

    /** 任務排程器（固定 8 執行緒） */
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(8);

    /** 每支 WorkingBeam 的執行中旗標 */
    private final Map<String, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();
    @Resource
    private MonitorPoolDispatcher dispatcher;


    @PostConstruct
    public void launchPerBeamMonitor() {
        for (String beamName : registry.getAllWorkingBeamNames()) {
            // 每個 beamName 配一個旗標
            runningFlags.put(beamName, new AtomicBoolean(false));

            executor.scheduleWithFixedDelay(() -> {
                AtomicBoolean flag = runningFlags.get(beamName);

                // 如果正在執行 → 略過本輪
                if (!flag.compareAndSet(false, true)) {
                    log.warn("[Monitor] WorkingBeam '{}' 尚在執行中，本輪跳過", beamName);
                    return;
                }

                Instant start = Instant.now();
                try {
                    dispatcher.submit("[dispatcher] WorkingBeam#{" + beamName + "} TaskMonitorLauncher", () ->
                            monitorPerDevice.monitorSingleBeam(beamName));
                    //monitorPerDevice.monitorSingleBeam(beamName);
                } catch (Exception e) {
                    log.error("[Monitor] WorkingBeam '{}' 任務監控失敗：{}", beamName, e.getMessage(), e);
                } finally {
                    flag.set(false); // 清除執行狀態
                    Duration duration = Duration.between(start, Instant.now());
                    //log.debug("[Monitor] WorkingBeam '{}' 任務完成，用時 {} ms", beamName, duration.toMillis());
                }
            }, 0, 50, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    public void shutdownExecutor() {
        log.info("[Shutdown] 停止 WorkingBeam 任務監控排程器...");
        executor.shutdown(); // 如需強制中斷可改 shutdownNow()
    }
}
