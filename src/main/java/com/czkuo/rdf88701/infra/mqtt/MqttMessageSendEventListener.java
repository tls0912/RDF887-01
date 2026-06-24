package com.czkuo.rdf88701.infra.mqtt;

import com.czkuo.rdf88701.application.service.mqtt.MqttDirectMessageSender;
import com.czkuo.rdf88701.domain.event.MqttMessageSendEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * MqttMessageSendEventListener
 * - 接收 MqttMessageSendEvent，實際發送 MQTT 訊息（透過 MessageSender）
 * - 包含詳細 log，便於除錯與查詢
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageSendEventListener {

    private final MqttDirectMessageSender messageSender;

    @EventListener
    public void handleMqttSend(MqttMessageSendEvent event) {
        try {
            // 發送訊息
            messageSender.send(event.system(), event.cmdId(), event.payload(), event.type(), event.tid());

            // 記錄成功發送 log
            log.info("[MQTT] 成功發送 → system={}, cmdId={}, tid={}, type={}, payload={}",
                    event.system(), event.cmdId(), event.tid(), event.type(), event.payload());

        } catch (Exception e) {
            // 記錄失敗 log
            log.error("[MQTT] 發送失敗 → system={}, cmdId={}, tid={}, type={}, payload={}, error={}",
                    event.system(), event.cmdId(), event.tid(), event.type(), event.payload(), e.getMessage(), e);
        }
    }
}
