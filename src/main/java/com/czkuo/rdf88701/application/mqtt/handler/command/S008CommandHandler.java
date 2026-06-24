package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.dto.mqtt.command.S007CommandPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.command.S008CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S008CommandHandler
 * - 處理 CMD_ID=S008 的指令（SEEC 傳送 WARNING 警告）
 * - 不需回傳 ACK，單向警告通知
 * - 處理流程：
 *   1. 記錄訊息至 mqtt_message_log
 *   2. 後續可擴充告警處理流程（例如推播 / UI 告警顯示）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S008CommandHandler extends AbstractCommandHandler<S008CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;
    private final MqttCommandService mqttCommandService;

    /**
     * 建構子：注入必要元件
     *
     * @param objectMapper           JSON 處理器（序列化 / 反序列化）
     * @param responseEventPublisher MQTT 回應事件發送器（本指令不需使用）
     * @param logService             訊息記錄服務（寫入 mqtt_message_log）
     * @param systemContext          系統上下文（取得我方代碼）
     */
    public S008CommandHandler(ObjectMapper objectMapper,
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
     * 處理 S008 指令主流程
     *
     * @param system  來源系統（如 seec）
     * @param topic   MQTT topic（如 seec_to_saa）
     * @param command 已反序列化的 payload（警告內容）
     * @param type    訊息類型（固定為 COMMAND）
     */
    @Override
    protected void process(String system, String topic, S008CommandPayload command, MqttMessageType type) throws Exception {
        log.warn("[S008] 收到 WARNING 事件：alid={}, alarmCode={}, topic={}, system={}",
                command.getMessage().getAlid(),
                command.getMessage().getAlarmCode(),
                topic,
                system
        );

        S008CommandPayload.Message msg = command.getMessage();

        // 1️⃣ 轉換成 JSON 結構（用於儲存）
        JsonNode payload = objectMapper.valueToTree(command);

        // 2️⃣ 記錄訊息至 mqtt_message_log（無需回應 ACK）
        logService.record(
                topic,                          // topic 名稱（如 seec_to_saa）
                system,                         // sender：對方系統
                systemContext.getSystemCode(),  // receiver：我方系統（動態取得）
                payload,                        // 原始 payload 結構
                MqttMessageType.COMMAND         // 訊息類型：COMMAND
        );

        // [可擴充] 推播至 UI、記錄本地警告歷程、觸發告警流程等
        mqttCommandService.sendS008("ase", msg.getDeviceName(), msg.getAlid(), msg.getAlidDescEn(), msg.getAlidDescCh(), msg.getAlarmCode());
    }

    /**
     * 回傳對應的 CMD_ID（供 Handler Router 快取與註冊）
     *
     * @return "S008"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S008";
    }

    /**
     * 回傳 payload 對應的 Java 類型（提供 Jackson 反序列化用）
     *
     * @return S008CommandPayload.class
     */
    @Override
    protected Class<S008CommandPayload> getCommandType() {
        return S008CommandPayload.class;
    }
}
