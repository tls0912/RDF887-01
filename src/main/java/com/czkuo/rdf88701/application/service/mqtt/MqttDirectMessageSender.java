package com.czkuo.rdf88701.application.service.mqtt;

import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.infra.mqtt.MqttClientManager;
import com.czkuo.rdf88701.infra.mqtt.PendingSendRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * MqttDirectMessageSender
 * - 直接透過 MQTT 客戶端同步發送訊息
 * - 發送成功後，審計寫表改為「非同步 + 限時」，避免阻塞 HTTP 執行緒
 *
 * 設計要點：
 * - publish 失敗：回傳 fail（呼叫端可據此決定是否補償/重送）
 * - publish 成功但審計失敗/逾時：仍回傳 success（best-effort 記錄），同時 warn log
 * - sender（本端系統）以設定值帶入，避免硬編碼 "saa"
 *
 * 注意：
 * - 送出用「原始 payload」；是否清洗/截斷由 MqttMessageLogService 負責（只針對寫 DB）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttDirectMessageSender {

    private final MqttClientManager mqttClientManager;
    private final MqttMessageLogService mqttMessageLogService;
    private final ObjectMapper objectMapper;

    /** 非同步審計用的執行器（daemon 執行緒，避免阻塞應用關閉） */
    private final Executor mqttAuditExecutor;

    /** 本機 pending registry，避免「對方 ACK 比 DB 寫入更快」導致型別誤判 */
    private final PendingSendRegistry pendingSendRegistry;

    /** 本端系統代碼（對應 mqtt.client.system），預設 "saa" */
    @Value("${mqtt.client.system:saa}")
    private String localSystem;

    /** 審計寫表最長等待（毫秒），逾時就放棄（避免卡 HTTP） */
    @Value("${mqtt.audit.timeout-ms:1000}")
    private long auditTimeoutMs;

    /** 只在已連線時才允許送出（mqtt.command.require-connected，預設 true） */
    @Value("${mqtt.command.require-connected:true}")
    private boolean requireConnected;

    /**
     * 發送 MQTT 訊息至指定系統，並記錄至資料庫（審計：非同步 + 限時）
     *
     * @param targetSystem 接收端系統（如 ase、seec）
     * @param cmdId        指令代碼（如 S001、S002）
     * @param payload      指令 JSON（原文）
     * @param type         指令類型（COMMAND 或 ACK）
     * @param tid          該筆指令的 TID
     */
    public MqttSendResult send(String targetSystem, String cmdId, String payload, MqttMessageType type, String tid) {
        final String sys = normalize(targetSystem);
        final String topic = mqttClientManager.getSendTopic(sys); // 先取 topic，log 用得到

        // 連線防呆：未連線就不送，回傳 fail（交由上游決定是否排程/重試）
        if (requireConnected && !mqttClientManager.isConnected(sys)) {
            log.info("[MQTT] skip publish (NOT CONNECTED): sys={}, tid={}", sys, tid);
            return MqttSendResult.fail("MQTT not connected", tid);
        }

        // 只對 COMMAND 做「送出前 pending 登記」
        boolean markedPending = false;
        if (type == MqttMessageType.COMMAND) {
            pendingSendRegistry.markPending(sys, tid, cmdId);
            markedPending = true;
        }

        // 1) 同步發送（用原文 payload）
        try {
            mqttClientManager.publish(sys, payload);
        } catch (MqttException e) {
            if (markedPending) {
                pendingSendRegistry.complete(sys, tid, cmdId);
            }
            String msg = e.getMessage();
            log.error("[MQTT] 發送至 {} 失敗：{}", sys, msg, e);
            return MqttSendResult.fail("MQTT 發送失敗：" + msg, tid);
        }

        // 2) 發送成功 → 非同步審計（清洗與截斷在 LogService 內處理）
        try {
            CompletableFuture
                    .runAsync(() -> {
                        try {
                            JsonNode json = objectMapper.readTree(payload);
                            mqttMessageLogService.record(topic, localSystem, sys, json, type);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }, mqttAuditExecutor)
                    .orTimeout(auditTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        log.warn("[MQTT] 已發送但記錄失敗/逾時：tid={}, topic={}, err={}", tid, topic, ex.toString());
                        return null;
                    });
        } catch (Exception ex) {
            log.warn("[MQTT] 審計提交非同步失敗：tid={}, err={}", tid, ex.toString(), ex);
        }

        // 3) 立即回應成功
        return MqttSendResult.success(tid);
    }

    private static String normalize(String s) {
        return (s == null) ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
