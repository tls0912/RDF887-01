package com.czkuo.rdf88701.domain.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * MqttMessageReceivedEvent
 * - 表示一筆接收到的 MQTT 訊息，包含來源系統（system）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
public class MqttMessageReceivedEvent extends ApplicationEvent {

    private final String system;
    private final String topic;
    private final String payload;

    public MqttMessageReceivedEvent(Object source, String system, String topic, String payload) {
        super(source);
        this.system = system;
        this.topic = topic;
        this.payload = payload;
    }
}
