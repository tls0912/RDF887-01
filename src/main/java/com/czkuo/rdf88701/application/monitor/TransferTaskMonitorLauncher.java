package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.config.plc.PlcTransferRegistry;
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
 * Transfer 任務監控啟動器（防重複執行版）
 * - 每台 Transfer 啟動獨立任務監控排程
 * - 若上一輪尚未完成，本輪排程會跳過
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferTaskMonitorLauncher {

    private final PlcTransferRegistry registry;
    private final TransferTaskMonitorPerDevice monitorPerDevice;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(8);

    /** 每台 Transfer 的執行狀態鎖定 */
    private final Map<String, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();
    @Resource
    private MonitorPoolDispatcher dispatcher;

    @PostConstruct
    public void launchPerTransferMonitor() {
        for (String transferName : registry.getAllTransferNames()) {
            runningFlags.put(transferName, new AtomicBoolean(false));

            executor.scheduleWithFixedDelay(() -> {
                AtomicBoolean flag = runningFlags.get(transferName);

                if (!flag.compareAndSet(false, true)) {
                    //log.debug("[Monitor] Transfer '{}' 上一輪尚未結束，跳過本輪", transferName);
                    return;
                }

                try {
                    dispatcher.submit("[dispatcher] Transfer#{" + transferName + "} TaskMonitorLauncher", () -> monitorPerDevice.monitorSingleTransfer(transferName));
                    //monitorPerDevice.monitorSingleTransfer(transferName);
                } catch (Exception e) {
                    log.error("[Monitor] Transfer '{}' 任務監控失敗：{}", transferName, e.getMessage(), e);
                } finally {
                    flag.set(false);
                }
            }, 0, 100, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    public void shutdownExecutor() {
        log.info("[Shutdown] 停止 Transfer 任務監控排程器...");
        executor.shutdown();
    }
}
