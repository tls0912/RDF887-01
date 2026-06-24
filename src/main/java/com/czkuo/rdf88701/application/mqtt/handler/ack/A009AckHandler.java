package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.A009AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A009AckHandler
 * - 負責處理 CMD_ID=A009 的 ACK 訊息（AGV 狀態回傳）
 * - SEEC 回覆多台 AGV 狀態
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] 更新 UI、狀態同步、推播等
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class A009AckHandler extends AbstractAckHandler<A009AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 發送器，用於橋接轉發到 ASE */
    private final MqttMessageEventPublisher publisher;

    public A009AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService,
                          MqttMessageEventPublisher publisher) {
        super(objectMapper);
        this.logService = logService;
        this.publisher = publisher;
    }

    /**
     * 處理收到的 A009 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     * 2. 橋接轉發 ACK 給 ASE（同一個 TID/CMD_ID）
     * 3. [可擴充] AGV 狀態推播/UI 顯示等
     */
    @Override
    protected void process(String system, String topic, A009AckPayload ack) throws Exception {
        int count = (ack.getMessage() != null && ack.getMessage().getData() != null)
                ? ack.getMessage().getData().size() : 0;
        log.info("[A009] 收到 AGV 狀態回傳 ACK：AGV_COUNT={}, RESULT={}, topic={}, system={}",
                count, ack.getResult(), topic, system);

        // 1️⃣ 記錄 ACK 至 mqtt_message_log
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        logService.record(
                topic,
                system,
                logService.getLocalSystem(),
                jsonPayload,
                MqttMessageType.ACK
        );

        // 2️⃣ 橋接轉發 ACK 給 ASE（保持同一個 TID/CMD_ID）
        try {
            String ackJson = objectMapper.writeValueAsString(ack);
            publisher.publish(
                    "ase",                 // 目標系統：ASE
                    ackJson,               // 完整 ACK payload
                    MqttMessageType.ACK,
                    ack.getTid(),          // 同一個 TID
                    "A009"                 // CMD_ID
            );
            log.info("[A009] 已轉發 ACK 給 ASE：tid={}, agvCount={}", ack.getTid(), count);
        } catch (Exception e) {
            log.error("[A009] 轉發 ASE 失敗：tid={}, err={}", ack.getTid(), e.getMessage(), e);
        }

        // 3️⃣ [可擴充] AGV 狀態推播/UI 顯示、資料同步等
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     */
    @Override
    protected String getCmdIdInternal() {
        return "A009";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     */
    @Override
    protected Class<A009AckPayload> getAckType() {
        return A009AckPayload.class;
    }
}
