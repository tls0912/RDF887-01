package com.czkuo.rdf88701.infra.scheduler;

import com.czkuo.rdf88701.common.enums.ConnectionMode;
import com.czkuo.rdf88701.config.plc.PlcDeviceRegistry;
import com.czkuo.rdf88701.config.plc.PlcProperties;
import com.czkuo.rdf88701.infra.adapter.plc.connection.PlcClientManager;
import com.czkuo.rdf88701.infra.adapter.plc.connection.PlcDeviceStatus;
import com.czkuo.rdf88701.infra.event.PlcEventPublisher;
import com.czkuo.rdf88701.infra.event.model.plc.connection.PlcDisconnectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * PLC 健康檢查排程器：
 * - 只處理 AUTO 模式裝置
 * - 檢查實際連線狀態，若斷線則觸發事件並進行自動重連
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcHealthScheduler {

    private final PlcClientManager clientManager;
    private final PlcDeviceRegistry deviceRegistry;
    private final PlcEventPublisher eventPublisher;

    /**
     * 每隔一段時間檢查所有 PLC 裝置的實際連線狀態。
     * 僅針對 AUTO 模式進行自動重連。
     */
    @Scheduled(fixedDelayString = "${plc.health-check-interval:10000}")
    public void checkAllDeviceConnections() {
        List<String> deviceNames = clientManager.getAllDeviceNames();

        for (String name : deviceNames) {
            PlcDeviceStatus status = clientManager.getStatus(name);
            if (status == null || status.getConnectionMode() != ConnectionMode.AUTO) {
                continue; // 略過非 AUTO 模式的裝置
            }

            boolean actuallyConnected = clientManager.isActuallyConnected(name);
            if (actuallyConnected) {
                // 若實體連線正常，不需處理
                continue;
            }

            PlcProperties.Device device = deviceRegistry.getDevice(name);
            boolean wasMarkedConnected = status.isConnected();

            if (wasMarkedConnected) {
                // 狀態從「已連線」→「實際斷線」
                status.markDisconnected("Health check failed");

                eventPublisher.publish(new PlcDisconnectedEvent(
                        name,
                        Instant.now(),
                        device.getIp(),
                        device.getProtocol(),
                        "Disconnected by health check",
                        ConnectionMode.AUTO,
                        PlcDisconnectedEvent.Reason.HEALTH_CHECK_FAILED
                ));

                log.warn("[PLC] 裝置 '{}' 在健康檢查中被判定為斷線，觸發事件", name);
            }

            // 嘗試重連（無論是否已發事件）
            clientManager.reconnectInternal(
                    name,
                    PlcDisconnectedEvent.Reason.HEALTH_CHECK_FAILED,
                    "Auto reconnect triggered by health check"
            );
        }
    }
}
