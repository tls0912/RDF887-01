package com.czkuo.rdf88701.application.mqtt.router;

import com.czkuo.rdf88701.application.mqtt.MqttMessageHandler;
import com.czkuo.rdf88701.application.mqtt.SupportsCommandId;
import com.czkuo.rdf88701.application.mqtt.util.MqttMessageTypeResolver;
import com.czkuo.rdf88701.application.service.mqtt.MqttEventOutboxService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.infra.mqtt.PendingSendRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MqttCommandRouter
 * - 使用 O(1) 快取查找結構，根據 CMD_ID + messageType 分派對應 Handler
 * - 配合 MqttMessageTypeResolver 判斷訊息是 COMMAND 或 ACK
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttCommandRouter {

    private final List<MqttMessageHandler> handlerList;
    private final MqttMessageTypeResolver messageTypeResolver;
    private final ObjectMapper objectMapper;
    private final MqttEventOutboxService outbox;
    private final PendingSendRegistry pendingSendRegistry;

    private static final String LOCAL_SYSTEM = "saa"; // 我方系統代碼，可改為 @Value 注入

    /**
     * handlerMap：CMD_ID + type 為 key 的快取表，用於快速查找對應的 Handler
     * 格式範例："S001::COMMAND"、"S001::ACK"
     */
    private final Map<String, MqttMessageHandler> handlerMap = new HashMap<>();

    /**
     * 組合 handler key
     */
    private String buildHandlerKey(String cmdId, MqttMessageType type) {
        return cmdId + "::" + type.name();
    }

    /**
     * 啟動時根據 handlerList 註冊所有支援的 CMD_ID + messageType 對應 Handler
     */
    @PostConstruct
    public void initHandlerMap() {
        for (MqttMessageHandler handler : handlerList) {

            if (!(handler instanceof SupportsCommandId cmdAware)) continue;

            String cmdId = cmdAware.getCmdId();
            if (!StringUtils.hasText(cmdId)) {
                log.warn("[MQTT] 忽略 CMD_ID 為空的 handler：{}", handler.getClass().getSimpleName());
                continue;
            }

            for (MqttMessageType type : cmdAware.getSupportedTypes()) {
                String key = buildHandlerKey(cmdId, type);
                if (handlerMap.containsKey(key)) {
                    log.warn("[MQTT] Handler 覆蓋：{} → {} (原為 {})",
                            key, handler.getClass().getSimpleName(),
                            handlerMap.get(key).getClass().getSimpleName());
                }
                handlerMap.put(key, handler);
                //log.debug("[MQTT] 註冊 Handler：{} → {}", key, handler.getClass().getSimpleName());
            }
        }
    }

    /**
     * 指令分派主邏輯
     * - 解析 CMD_ID 與 MessageType
     * - 查找對應 Handler 並執行處理
     */
    public void route(String system, String topic, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String cmdId = root.path("CMD_ID").asText();
            if (!StringUtils.hasText(cmdId)) {
                log.warn("[MQTT] CMD_ID 缺失，忽略處理：system={}, topic={}", system, topic);
                return;
            }

            MqttMessageType type = messageTypeResolver.resolve(payload, system, LOCAL_SYSTEM);

            // 防呆：UNKNOWN 不分派，避免亂入
            if (type == MqttMessageType.UNKNOWN) {
                log.warn("[MQTT] 無法判斷型別（UNKNOWN），忽略處理：system={}, cmdId={}, topic={}", system, cmdId, topic);
                return;
            }

            String key = buildHandlerKey(cmdId, type);
            MqttMessageHandler handler = handlerMap.get(key);

            if (type == MqttMessageType.ACK) {
                // 盡量兼容大小寫／命名（RESULT / result、RESULT_MESSAGE / result_message）
                String tid         = text(root, "TID");
                String resultText  = coalesce(text(root, "RESULT"), text(root, "result"));
                String detail      = coalesce(text(root, "RESULT_MESSAGE"), text(root, "result_message"));

                // 1) 先清掉 pending（避免後續同 tid/cmd 又被誤判）
                boolean isPending = false;
                try {
                    isPending = pendingSendRegistry.isPendingFrom(system, tid, cmdId);
                    if (isPending) {
                        // 只有真的 pending 才清掉 + mark acked
                        pendingSendRegistry.complete(system, tid, cmdId);
                        outbox.markAcked(tid, resultText, detail);
                    } else {
                        //log.debug("[MQTT][ACK] (cmd={}, tid={}) 非 pending → 略過 outbox.markAcked", cmdId, tid);
                    }
                } catch (Exception ignore) {
                    log.warn("[MQTT][ACK] 檢查 pending 例外 (cmd={}, tid={})：{}", cmdId, tid, ignore.getMessage());
                }
            }

            if (handler != null && handler.supports(system, topic, payload, type)) {
                //log.debug("[MQTT] [{}] CMD_ID={} → {}", type, cmdId, handler.getClass().getSimpleName());
                handler.handle(system, topic, payload, type);
            } else {
                log.warn("[MQTT] 無 Handler 處理 CMD_ID={}，type={}，system={}，topic={}", cmdId, type, system, topic);
            }

        } catch (Exception e) {
            log.error("[MQTT] route() JSON 解析或 dispatch 失敗：{}", e.getMessage(), e);
        }
    }

    // ---- 小工具：讀字串欄位（若無值回空字串），以及 coalesce ----
    private static String text(JsonNode n, String field) {
        JsonNode x = n.get(field);
        return x == null ? "" : x.asText("");
    }

    private static String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : (b == null ? "" : b);
    }
}
