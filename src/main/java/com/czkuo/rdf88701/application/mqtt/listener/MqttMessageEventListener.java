package com.czkuo.rdf88701.application.mqtt.listener;

import com.czkuo.rdf88701.application.mqtt.router.MqttCommandRouter;
import com.czkuo.rdf88701.domain.event.MqttMessageReceivedEvent;
import com.czkuo.rdf88701.infra.mqtt.InboundDedupRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 接收 MQTT 事件並轉發至 CommandRouter
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageEventListener {

    private final MqttCommandRouter mqttCommandRouter;
    private final ObjectMapper mapper;
    private final InboundDedupRegistry inboundDedupRegistry;

    @EventListener
    public void onApplicationEvent(MqttMessageReceivedEvent event) {
        String system = event.getSystem();
        String topic = event.getTopic();
        String payload = event.getPayload();

        try {
            JsonNode node = mapper.readTree(payload); // 先解析成樹狀結構
            String compactPayload = mapper.writeValueAsString(node); // 轉成一行格式

            String cmdId = node.path("CMD_ID").asText();
            String tid   = node.path("TID").asText();
            // 入站去重（同 sender+CMD_ID+TID 在 TTL 內且 payload 相同 → 丟掉）
            if (!inboundDedupRegistry.firstSeen(system, cmdId, tid, compactPayload)) {
                log.info("[MQTT][INBOX] drop duplicate/replay: sys={}, cmdId={}, tid={}, payload={}", system, cmdId, tid, compactPayload);
                // return;
            }

            //log.debug("[MQTT][RECV] system={}, topic={}, payload={}", system, topic, compactPayload);
            mqttCommandRouter.route(system, topic, compactPayload);
        } catch (Exception e) {
            log.error("[MQTT][ERROR] 處理訊息失敗，topic={}, error={}", topic, e.getMessage(), e);
        }
    }
}
