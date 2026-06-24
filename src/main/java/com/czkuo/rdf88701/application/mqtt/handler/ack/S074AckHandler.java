package com.czkuo.rdf88701.application.mqtt.handler.ack;


import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S074AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S074AckHandler
 * - 負責處理 CMD_ID=S074 的 ACK 訊息（任務查詢回覆）
 * - 廠商回覆當前待處理任務清單
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] UI 呈現、任務資料入庫、異常提示等
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
public class S074AckHandler extends AbstractAckHandler<S074AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /**
     * 建構子
     *
     * @param objectMapper JSON 處理器
     * @param logService   MQTT 訊息記錄服務
     */
    public S074AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 S074 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     * 2. [可擴充] 根據回覆內容做 UI 呈現、異常提示等
     *
     * @param system 發送方系統
     * @param topic  MQTT topic
     * @param ack    已反序列化的 S074AckPayload
     */
    @Override
    protected void process(String system, String topic, S074AckPayload ack) throws Exception {
        int count = (ack.getMessage() != null && ack.getMessage().getMissionList() != null)
                ? ack.getMessage().getMissionList().size() : 0;
        log.info("[S074] 收到任務查詢回覆 ACK：result={}, 任務數={}, topic={}, system={}",
                ack.getResult(), count, topic, system);

        // 1️⃣ 記錄 ACK 至 mqtt_message_log
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        logService.record(
                topic,                        // MQTT topic
                system,                       // sender（對方系統）
                logService.getLocalSystem(),  // receiver（我方系統）
                jsonPayload,
                MqttMessageType.ACK
        );

        // 2️⃣ [可擴充] 可將清單顯示於 UI 或記錄於本地
        // 目前僅保留後續處理入口，尚未實作額外流程。
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S074"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S074";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S074AckPayload.class
     */
    @Override
    protected Class<S074AckPayload> getAckType() {
        return S074AckPayload.class;
    }
}
