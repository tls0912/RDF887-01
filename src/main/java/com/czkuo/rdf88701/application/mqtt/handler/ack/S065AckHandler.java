package com.czkuo.rdf88701.application.mqtt.handler.ack;


import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S065AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S065AckHandler
 * - 負責處理 CMD_ID=S065 的 ACK 訊息（標籤資訊印製回應）
 * - 廠商回傳標籤印製處理結果（OK/NG）
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] 根據回應結果進行 UI 顯示、任務狀態變更等
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
public class S065AckHandler extends AbstractAckHandler<S065AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /**
     * 建構子
     *
     * @param objectMapper JSON 處理器
     * @param logService   MQTT 訊息記錄服務
     */
    public S065AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 S065 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     * 2. [可擴充] 根據結果進行顯示、任務狀態更新等
     *
     * @param system 發送方系統
     * @param topic  MQTT topic
     * @param ack    已反序列化的 S065AckPayload
     */
    @Override
    protected void process(String system, String topic, S065AckPayload ack) throws Exception {
        log.info("[S065] 收到標籤資訊印製 ACK：result={}, topic={}, system={}",
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

        // 2️⃣ [可擴充] 根據結果執行 UI 顯示、任務狀態變更、異常警示等
        // 目前僅保留 result 後續處理入口，尚未實作狀態處理。
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S065"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S065";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S065AckPayload.class
     */
    @Override
    protected Class<S065AckPayload> getAckType() {
        return S065AckPayload.class;
    }
}
