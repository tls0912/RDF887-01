package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.command.A010CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A010CommandHandler
 * - 負責處理 CMD_ID=A010 的指令（AGV 狀態定時回拋）
 * - SEEC → SAA 回傳多台 AGV 當前狀態與正在執行的任務
 * - 處理流程：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. [預留] 處理多台 AGV 狀態同步、推播等
 *   3. [本指令不回 ACK]
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class A010CommandHandler extends AbstractCommandHandler<A010CommandPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 系統識別（如 SAA/SEEC），由 context 提供 */
    private final SystemContext systemContext;

    private final MqttCommandService mqttCommandService;

    public A010CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              MqttCommandService mqttCommandService) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.mqttCommandService = mqttCommandService;
    }

    /**
     * 處理收到的 A010 指令
     * <p>
     * 1. 記錄多台 AGV 狀態與任務回拋 COMMAND 至資料庫
     * 2. [預留] 狀態分派、資料同步、UI 推播等
     * 3. [本指令不回 ACK]
     */
    @Override
    protected void process(String system, String topic, A010CommandPayload command, MqttMessageType type) throws Exception {
        List<A010CommandPayload.AgvStatus> agvList = (command.getMessage() != null) ? command.getMessage().getData() : null;
        int agvCount = (agvList != null) ? agvList.size() : 0;
        log.info("[A010] 收到 AGV 狀態定時回拋：TID={}, topic={}, system={}, AGV_COUNT={}",
                command.getTid(), topic, system, agvCount);

        // 1️⃣ 記錄 COMMAND 至 mqtt_message_log
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,
                systemContext.getSystemCode(),
                payload,
                MqttMessageType.COMMAND
        );

        // 2️⃣ [預留] 多台 AGV 狀態同步、資料庫寫入、UI 更新等
        mqttCommandService.sendA010("ase", agvList);
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     */
    @Override
    protected String getCmdIdInternal() {
        return "A010";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     */
    @Override
    protected Class<A010CommandPayload> getCommandType() {
        return A010CommandPayload.class;
    }
}
