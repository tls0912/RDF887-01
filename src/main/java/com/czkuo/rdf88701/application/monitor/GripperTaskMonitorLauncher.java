package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.config.plc.PlcGripperRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gripper 任務監控 launcher。
 *
 * <p>啟動時依 Gripper registry 為每台設備建立獨立排程，使用 AtomicBoolean
 * 防止同一台 Gripper 上一輪尚未完成時重入，實際任務推進委派給
 * GripperTaskMonitorPerDevice。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GripperTaskMonitorLauncher {

    private final PlcGripperRegistry registry;
    private final GripperTaskMonitorPerDevice monitorPerDevice;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(8);

    @Resource
    private MonitorPoolDispatcher dispatcher;


    /**
     * 每台 Gripper 的執行狀態鎖定
     */
    private final Map<String, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    @PostConstruct
    public void launchPerGripperMonitor() {
        for (String gripperName : registry.getAllGripperNames()) {
            runningFlags.put(gripperName, new AtomicBoolean(false));

            executor.scheduleWithFixedDelay(() -> {
                AtomicBoolean flag = runningFlags.get(gripperName);

                if (!flag.compareAndSet(false, true)) {
                    //log.debug("[Monitor] Gripper '{}' 上一輪尚未結束，跳過本輪", gripperName);
                    return;
                }

                try {
                    dispatcher.submit("[dispatcher] Gripper#{" + gripperName + "} TaskMonitorLauncher", () -> monitorPerDevice.monitorSingleGripper(gripperName));
                    //monitorPerDevice.monitorSingleGripper(gripperName);
                } catch (Exception e) {
                    log.error("[Monitor] Gripper '{}' 任務監控失敗：{}", gripperName, e.getMessage(), e);
                } finally {
                    flag.set(false);
                }
            }, 0, 50, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    public void shutdownExecutor() {
        log.info("[Shutdown] 停止 Gripper 任務監控排程器...");
        executor.shutdown();
    }
}
