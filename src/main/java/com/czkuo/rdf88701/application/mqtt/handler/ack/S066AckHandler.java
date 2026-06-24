package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S066AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S066AckHandler
 * - 負責處理 CMD_ID=S066 的 ACK 訊息（標籤資訊印製結果回覆）
 * - 廠商回覆標籤列印請求的處理結果
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] 根據回覆結果執行顯示、任務狀態調整等
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
public class S066AckHandler extends AbstractAckHandler<S066AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /**
     * 建構子
     *
     * @param objectMapper JSON 處理器
     * @param logService   MQTT 訊息記錄服務
     */
    public S066AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 S066 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     * 2. [可擴充] 根據結果進行顯示、任務狀態調整等
     *
     * @param system 發送方系統
     * @param topic  MQTT topic
     * @param ack    已反序列化的 S066AckPayload
     */
    @Override
    protected void process(String system, String topic, S066AckPayload ack) throws Exception {
        log.info("[S066] 收到標籤資訊印製（格式二）ACK：result={}, topic={}, system={}",
                ack.getResult(),
                topic,
                system
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

        // 2️⃣ [可擴充] 根據回覆結果做 UI 呈現、任務狀態變更、異常處理等
        // 目前僅保留 result 後續處理入口，尚未實作相應流程。
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S066"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S066";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S066AckPayload.class
     */
    @Override
    protected Class<S066AckPayload> getAckType() {
        return S066AckPayload.class;
    }
}
