package com.czkuo.rdf88701.application.mqtt.publisher;

import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.event.MqttMessageSendEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * MQTT 出站事件發布器。
 *
 * <p>將已組好的 MQTT COMMAND 或 ACK 包成 Spring Event 發送，讓 Handler 或
 * Service 不直接依賴底層 MQTT client，實際傳輸與 outbox 追蹤由 infra/application
 * 其他元件接續處理。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
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
