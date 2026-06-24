package com.czkuo.rdf88701.infra.event.model.plc.handler;

import com.czkuo.rdf88701.application.service.polling.scheduler.PollingTaskScheduler;
import com.czkuo.rdf88701.infra.event.model.plc.connection.PlcConnectedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.connection.PlcDisconnectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * PLC 連線事件處理器：
 * 負責處理連線成功與失敗的後續行為（如啟動或停止輪詢）。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcEventHandler {

    private final PollingTaskScheduler pollingTaskScheduler;

    /**
     * 連線成功時恢復對應裝置的輪詢任務
     */
    @EventListener
    public void onPlcConnected(PlcConnectedEvent event) {
        String deviceName = event.getDeviceName();
        log.info("[EVENT] 收到裝置 '{}' 的連線成功事件，準備恢復輪詢任務", deviceName);
        pollingTaskScheduler.resumePolling(deviceName);
    }

    /**
     * 連線失敗或中斷時暫停對應裝置的輪詢任務
     */
    @EventListener
    public void onPlcDisconnected(PlcDisconnectedEvent event) {
        String deviceName = event.getDeviceName();
        log.info("[EVENT] 收到裝置 '{}' 的連線中斷事件，準備停止輪詢任務", deviceName);
        pollingTaskScheduler.stopPolling(deviceName);
    }
}
