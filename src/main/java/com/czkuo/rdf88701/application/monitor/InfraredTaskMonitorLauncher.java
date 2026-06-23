package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.config.plc.PlcInfraredRegistry;
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
 * Infrared 任務監控啟動器
 * - 每台 Infrared 啟動獨立任務監控排程（每秒執行一次）
 * - 若該設備上一次任務尚未完成，則本輪排程會略過（避免併發）
 * - 每隻 Infrared 皆以 sensorName 作為獨立控制
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InfraredTaskMonitorLauncher {

    private final PlcInfraredRegistry registry;
    private final InfraredTaskMonitorPerDevice monitorPerDevice;

    /** 任務排程器（固定 8 執行緒） */
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(8);

    /** 每支 Infrared 的執行中旗標 */
    private final Map<String, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();
    @Resource
    private MonitorPoolDispatcher dispatcher;

    @PostConstruct
    public void launchPerSensorMonitor() {
        for (String sensorName : registry.getAllInfraredNames()) {
            runningFlags.put(sensorName, new AtomicBoolean(false));

            executor.scheduleWithFixedDelay(() -> {
                AtomicBoolean flag = runningFlags.get(sensorName);

                if (!flag.compareAndSet(false, true)) {
                    log.warn("[Monitor] Infrared '{}' 尚在執行中，本輪跳過", sensorName);
                    return;
                }

                Instant start = Instant.now();
                try {
                    dispatcher.submit("[dispatcher] WorkingBeam#{" + sensorName + "} TaskMonitorLauncher", () -> monitorPerDevice.monitorSingleInfrared(sensorName));
                    //monitorPerDevice.monitorSingleInfrared(sensorName);
                } catch (Exception e) {
                    log.error("[Monitor] Infrared '{}' 任務監控失敗：{}", sensorName, e.getMessage(), e);
                } finally {
                    flag.set(false);
                    Duration duration = Duration.between(start, Instant.now());
                    //log.debug("[Monitor] Infrared '{}' 任務完成，用時 {} ms", sensorName, duration.toMillis());
                }
            }, 0, 100, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    public void shutdownExecutor() {
        log.info("[Shutdown] 停止 Infrared 任務監控排程器...");
        executor.shutdown();
    }
}
