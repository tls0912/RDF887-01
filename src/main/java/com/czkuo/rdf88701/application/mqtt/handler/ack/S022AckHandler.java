package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S022AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S022AckHandler
 * - 負責處理 CMD_ID=S022 的 ACK 訊息（設備控制狀態查詢回覆）
 * - 廠商回覆設備名稱與目前控制狀態（REMOTE/LOCAL/MANUAL）
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] 根據控制狀態進行後續通知或異常補救
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
public class S022AckHandler extends AbstractAckHandler<S022AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /**
     * 建構子
     *
     * @param objectMapper JSON 處理器
     * @param logService   MQTT 訊息記錄服務
     */
    public S022AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 S022 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     * 2. [可擴充] 根據控制狀態進行後續處理
     *
     * @param system 發送方系統
     * @param topic  MQTT topic
     * @param ack    已反序列化的 S022AckPayload
     */
    @Override
    protected void process(String system, String topic, S022AckPayload ack) throws Exception {
        // 1️⃣ 日誌顯示收到的控制狀態資訊
        String deviceName = ack.getMessage() != null ? ack.getMessage().getDeviceName() : "";
        String status = ack.getMessage() != null ? ack.getMessage().getStatus() : "";

        log.info("[S022] 收到設備控制狀態查詢 ACK：result={}, topic={}, system={}, deviceName={}, status={}",
                ack.getResult(),
                topic,
                system,
                deviceName,
                status
        );

        // 2️⃣ 記錄 ACK 至 mqtt_message_log
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        logService.record(
                topic,                          // MQTT topic
                system,                         // sender（對方系統）
                logService.getLocalSystem(),    // receiver（我方系統）
                jsonPayload,
                MqttMessageType.ACK
        );

        // 3️⃣ [可擴充] 根據狀態進行異常警示或流程控制
        // 目前僅保留控制狀態後續處理入口，尚未實作異常或流程通知。
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S022"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S022";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S022AckPayload.class
     */
    @Override
    protected Class<S022AckPayload> getAckType() {
        return S022AckPayload.class;
    }
}
