package com.czkuo.rdf88701.application.mqtt.publisher;

import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.event.MqttMessageSendEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * MqttMessageEventPublisher
 * - 將組好的 MQTT 指令或回應訊息，以 Spring Event 發送
 * - 與底層 MQTT 傳輸邏輯解耦（透過 EventListener 處理發送）
 */
@Component
@RequiredArgsConstructor
public class MqttMessageEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 發送 MQTT 發送事件
     *
     * @param system   目標對象（如 seec / ase）
     * @param payload  JSON 格式的 MQTT 訊息
     * @param type     訊息類型（COMMAND 或 ACK）
     * @param tid      指令唯一識別碼（TID）
     * @param cmdId    指令代號（如 S001、R007）
     */
    public void publish(String system, String payload, MqttMessageType type, String tid, String cmdId) {
        eventPublisher.publishEvent(new MqttMessageSendEvent(system, payload, type, tid, cmdId));
    }
}
