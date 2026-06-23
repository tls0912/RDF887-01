package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.command.S007CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S007CommandHandler
 * - 處理 CMD_ID=S007 的指令（SEEC 發送警報事件通知）
 * - 此為單向指令，無需回傳 ACK。
 * - 處理內容包含：
 *   1. 記錄原始 MQTT 指令至 mqtt_message_log
 *   2. 印出警報資訊（可擴充寫入警報資料表）
 */
@Slf4j
@Component
public class S007CommandHandler extends AbstractCommandHandler<S007CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;
    private final MqttCommandService mqttCommandService;

    /**
     * 建構子
     *
     * @param objectMapper           JSON 處理器
     * @param responseEventPublisher Spring Event 發送器（此指令無 ACK 不使用）
     * @param logService             資料記錄服務，用於寫入 mqtt_message_log
     * @param systemContext          系統上下文，用於取得我方代碼（如 "saa"）
     */
    public S007CommandHandler(ObjectMapper objectMapper,
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
     * 執行 S007 指令處理邏輯
     *
     * @param system  發送系統代碼（如 seec）
     * @param topic   MQTT topic（如 seec_to_saa）
     * @param command 已反序列化之 payload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, S007CommandPayload command, MqttMessageType type) throws Exception {
        S007CommandPayload.Message msg = command.getMessage();

        // 1️⃣ 印出收到的警報內容
        log.info("[S007] 收到 ALARM 事件：device={}, alid={}, descCH={}, alarmCode={}, from={}, topic={}",
                msg.getDeviceName(),
                msg.getAlid(),
                msg.getAlidDescCh(),
                msg.getAlarmCode(),
                system,
                topic
        );

        // 2️⃣ 記錄 MQTT 訊息（type=COMMAND）
        JsonNode jsonPayload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,
                systemContext.getSystemCode(),
                jsonPayload,
                MqttMessageType.COMMAND
        );

        // [預留擴充] 可寫入 alarm_log、推播通知等
        mqttCommandService.sendS007("ase", msg.getDeviceName(), msg.getAlid(), msg.getAlidDescEn(), msg.getAlidDescCh(), msg.getAlarmCode());
    }

    /**
     * 回傳指令代碼（供 Router 註冊識別）
     *
     * @return "S007"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S007";
    }

    /**
     * 指定 payload 類型，供 Jackson 反序列化
     *
     * @return S007CommandPayload.class
     */
    @Override
    protected Class<S007CommandPayload> getCommandType() {
        return S007CommandPayload.class;
    }
}
