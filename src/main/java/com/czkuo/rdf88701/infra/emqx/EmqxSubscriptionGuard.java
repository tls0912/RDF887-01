package com.czkuo.rdf88701.infra.emqx;

import com.czkuo.rdf88701.config.mqtt.EmqxRestProperties;
import com.czkuo.rdf88701.config.mqtt.MqttConfigProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * EMQX 訂閱巡檢 + 自動補訂閱（RestClient 版, 不需 WebFlux）
 *
 * 每 30 秒：
 *   1) 讀取 mqtt.connections 各 system 的 clientId
 *   2) /api/v5/clients/{id} 確認在線
 *   3) /api/v5/subscriptions?clientid={id} 取得實際訂閱
 *   4) 與 ExpectedSubscriptionsRegistry 的「期望訂閱」比對，缺少則 bulk 補訂閱
 *
 * 預設「期望訂閱」包含各 system 的 recvTopic（QoS=0）；
 * 若 emqx.expected-subscriptions[system] 有配置，則以覆寫清單為準（可多 topic 與 QoS）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmqxSubscriptionGuard {

    private final RestClient emqxRestClient;
    private final ExpectedSubscriptionsRegistry expectedRegistry;
    private final MqttConfigProperties mqttProps;
    private final ObjectMapper objectMapper;

    /* ====== EMQX /api/v5 回傳模型（只保留用到的欄位） ====== */
    @Data
    public static class ClientView {
        private String clientid;
        private Integer subscriptions_cnt;
        private Boolean connected;
    }

    @Data
    public static class SubView {
        private String clientid;
        private String topic;
        private Integer qos;
    }

    /** bulk 訂閱的請求項目（避免 Map.of 型別推斷陷阱） */
    record BulkSubscribeItem(String topic, int qos) {}

    /** 啟動 10s 後跑第一次；之後每 30s 巡檢 */
    @Scheduled(initialDelay = 10_000, fixedDelay = 30_000)
    public void verifyAndHeal() {
        Map<String, List<EmqxRestProperties.TopicQos>> expected = expectedRegistry.build();

        expected.forEach((system, topics) -> {
            String clientId = getClientId(system);
            if (clientId == null || clientId.isBlank()) {
                log.warn("[EMQX][{}] 略過：未設定 clientId", system);
                return;
            }

            try {
                // 1) 讀 client 狀態（容錯兩種形狀）
                ClientView cv = fetchClientView(clientId);
                if (cv == null || Boolean.FALSE.equals(cv.getConnected())) {
                    log.warn("[EMQX][{}] client 不存在或未連線：{}", system, clientId);
                    return;
                }

                // 2) 讀訂閱清單（容錯兩種形狀）
                List<SubView> actual = fetchSubscriptions(clientId);

                // 3) 比對缺漏（或 QoS 不符）
                Map<String, Integer> should = topics.stream()
                        .collect(Collectors.toMap(
                                EmqxRestProperties.TopicQos::getTopic,
                                EmqxRestProperties.TopicQos::getQos,
                                (a, b) -> b,
                                LinkedHashMap::new));

                for (SubView s : actual) {
                    Integer expQos = should.get(s.getTopic());
                    if (expQos != null && Objects.equals(expQos, s.getQos())) {
                        should.remove(s.getTopic()); // 已滿足
                    }
                }

                if (should.isEmpty()) return;

                // 4) bulk 補訂閱
                List<BulkSubscribeItem> payload = should.entrySet().stream()
                        .map(e -> new BulkSubscribeItem(e.getKey(), e.getValue()))
                        .toList();

                log.warn("[EMQX][{}] 發現訂閱缺漏（clientId={}），準備 bulk 補上：{}", system, clientId, payload);

                emqxRestClient.post()
                        .uri("/api/v5/clients/{id}/subscribe/bulk", clientId)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity(); // 2xx 即視為成功

                log.info("[EMQX][{}] bulk subscribe 完成（clientId={}），補上 {} 項", system, clientId, payload.size());

            } catch (Exception ex) {
                log.error("[EMQX][{}] SubscriptionGuard 失敗：{}", system, ex.getMessage(), ex);
            }
        });
    }

    /** 從 mqtt.connections 取 system 對應的 clientId；若無則回 null */
    private String getClientId(String system) {
        try {
            var conn = mqttProps.getConnections().get(system);
            return (conn == null) ? null : conn.getClientId();
        } catch (Exception ignore) {
            return null;
        }
    }

    /* ===================== 封裝：呼叫 & 解析 ===================== */

    private ClientView fetchClientView(String clientId) {
        try {
            ResponseEntity<String> entity = emqxRestClient.get()
                    .uri("/api/v5/clients/{id}", clientId)
                    .retrieve()
                    .toEntity(String.class);

            if (!entity.getStatusCode().is2xxSuccessful() || entity.getBody() == null) return null;
            return parseClientView(entity.getBody());
        } catch (RestClientResponseException e) {
            // 例如：{"code":"CLIENTID_NOT_FOUND","message":"Client ID not found"}
            log.warn("[EMQX] 讀取 client 失敗（{}）：{}", clientId, safeBody(e));
            return null;
        }
    }

    private List<SubView> fetchSubscriptions(String clientId) {
        try {
            ResponseEntity<String> entity = emqxRestClient.get()
                    .uri("/api/v5/subscriptions?clientid={cid}", clientId)
                    .retrieve()
                    .toEntity(String.class);

            if (!entity.getStatusCode().is2xxSuccessful() || entity.getBody() == null) return List.of();
            return parseSubscriptions(entity.getBody());
        } catch (RestClientResponseException e) {
            log.warn("[EMQX] 讀取 subscriptions 失敗（{}）：{}", clientId, safeBody(e));
            return List.of();
        }
    }

    private ClientView parseClientView(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode node = root.has("data") ? root.get("data") : root; // 支援 {"data":{...}} 或直接 {...}
            if (node != null && node.isObject()) {
                return objectMapper.treeToValue(node, ClientView.class);
            }
        } catch (Exception e) {
            log.error("[EMQX] 解析 client JSON 失敗：{}", e.getMessage(), e);
        }
        return null;
    }

    private List<SubView> parseSubscriptions(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode node = root;

            // 支援 {"data":[...]}、[...]、{"items":[...]}（以防未來格式變動）
            if (root.has("data")) node = root.get("data");
            else if (root.has("items")) node = root.get("items");

            if (node != null && node.isArray()) {
                List<SubView> list = new ArrayList<>();
                for (JsonNode n : node) {
                    list.add(objectMapper.treeToValue(n, SubView.class));
                }
                return list;
            }
            // 若不是陣列（極端情況），回空
        } catch (Exception e) {
            log.error("[EMQX] 解析 subscriptions JSON 失敗：{}", e.getMessage(), e);
        }
        return List.of();
    }

    private static String safeBody(RestClientResponseException e) {
        try {
            return e.getResponseBodyAsString();
        } catch (Exception ignore) {
            return e.getMessage();
        }
    }
}
