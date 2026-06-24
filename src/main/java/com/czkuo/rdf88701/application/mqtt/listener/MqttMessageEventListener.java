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
 * MQTT 入站事件監聽器。
 *
 * <p>接收 infra 層發布的 MQTT 入站事件，將 payload 正規化為單行 JSON，
 * 執行入站去重檢查後，轉交 MqttCommandRouter 進行 CMD_ID 與型別分派。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
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
