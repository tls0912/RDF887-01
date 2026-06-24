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
 * Transfer 任務監控 launcher。
 *
 * <p>啟動時依 Transfer registry 為每台設備建立獨立排程，使用 AtomicBoolean
 * 防止同一台 Transfer 上一輪尚未完成時重入，實際任務推進委派給
 * TransferTaskMonitorPerDevice。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
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
