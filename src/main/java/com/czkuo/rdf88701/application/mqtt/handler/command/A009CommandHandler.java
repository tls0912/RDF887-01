package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.A009AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.A009CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A009CommandHandler
 * - 負責處理 CMD_ID=A009 的指令（詢問 AGV 車輛狀態）
 * - SAA → SEEC 發送查詢指令
 * - 處理流程：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. [預留] 業務查詢 AGV 狀態
 *   3. [本指令需等待 SEEC 回 ACK]
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class A009CommandHandler extends AbstractCommandHandler<A009CommandPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 系統識別（如 SAA/SEEC），由 context 提供 */
    private final SystemContext systemContext;

    public A009CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
    }

    /**
     * 處理收到的 A009 指令
     * <p>
     * 1. 記錄查詢 AGV 狀態 COMMAND 至資料庫
     * 2. 橋接轉發至 SEEC（等待對方回 ACK）
     * 3. [此指令不在此回 ACK]
     */
    @Override
    protected void process(String system, String topic, A009CommandPayload command, MqttMessageType type) throws Exception {
        log.info("[A009] 收到 AGV 狀態查詢指令：TID={}, topic={}, system={}", command.getTid(), topic, system);

        // 1️⃣ 記錄 COMMAND 至 mqtt_message_log
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,
                systemContext.getSystemCode(),
                payload,
                MqttMessageType.COMMAND
        );

        // 2️⃣ 橋接轉發 COMMAND 給 SEEC（不在此組 ACK；同一個 TID/CMD_ID）
        try {
            String json = objectMapper.writeValueAsString(command);
            responseEventPublisher.publish(
                    "seec",               // 目標系統：SEEC
                    json,                 // 完整 payload
                    MqttMessageType.COMMAND,
                    command.getTid(),     // 同一個 TID
                    "A009"                // CMD_ID
            );
            log.info("[A009] 已轉發 COMMAND 給 SEEC：tid={}", command.getTid());
        } catch (Exception e) {
            log.error("[A009] 轉發 SEEC 失敗：tid={}, err={}", command.getTid(), e.getMessage(), e);
        }

        // 3️⃣ [不回 ACK]：等待 SEEC 依 A009 規格回傳 ACK
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
    protected Class<A009CommandPayload> getCommandType() {
        return A009CommandPayload.class;
    }
}
