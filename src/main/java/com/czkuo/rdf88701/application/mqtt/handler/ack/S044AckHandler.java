package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S044AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S044AckHandler
 * - 負責處理 CMD_ID=S044 的 ACK 訊息（安全 Sensor 清單查詢回覆）
 * - 廠商回覆目前系統安全設備名稱與功能說明
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] 根據裝置清單進行顯示、比對或告警等後續處理
 */
@Slf4j
@Component
public class S044AckHandler extends AbstractAckHandler<S044AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /**
     * 建構子
     *
     * @param objectMapper JSON 處理器
     * @param logService   MQTT 訊息記錄服務
     */
    public S044AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 S044 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     * 2. [可擴充] 根據裝置清單做顯示、比對、異常通知等
     *
     * @param system 發送方系統
     * @param topic  MQTT topic
     * @param ack    已反序列化的 S044AckPayload
     */
    @Override
    protected void process(String system, String topic, S044AckPayload ack) throws Exception {
        int deviceCount = ack.getMessage() != null && ack.getMessage().getSafetyDeviceList() != null
                ? ack.getMessage().getSafetyDeviceList().size() : 0;
        log.info("[S044] 收到安全 Sensor 清單查詢 ACK：result={}, topic={}, system={}, deviceCount={}",
                ack.getResult(),
                topic,
                system,
                deviceCount
        );

        // 1️⃣ 記錄 ACK 至 mqtt_message_log
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        logService.record(
                topic,                          // MQTT topic
                system,                         // sender（對方系統）
                logService.getLocalSystem(),    // receiver（我方系統）
                jsonPayload,
                MqttMessageType.ACK
        );

        // 2️⃣ [可擴充] 根據安全裝置清單進行 UI 顯示、設備比對或異常告警
        // TODO: 裝置清單顯示/比對/告警等業務
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S044"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S044";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S044AckPayload.class
     */
    @Override
    protected Class<S044AckPayload> getAckType() {
        return S044AckPayload.class;
    }
}
