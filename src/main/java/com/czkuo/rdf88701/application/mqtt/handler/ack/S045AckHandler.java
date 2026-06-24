package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S045AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S045AckHandler
 * - 負責處理 CMD_ID=S045 的 ACK 訊息（安全 Sensor 狀態確認）
 * - 廠商回覆 ASE 發來的設備狀態是否接收成功，可附帶設備清單
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] 根據設備狀態進行告警、顯示、異常處理
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
public class S045AckHandler extends AbstractAckHandler<S045AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /**
     * 建構子
     *
     * @param objectMapper JSON 處理器
     * @param logService   MQTT 訊息記錄服務
     */
    public S045AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 S045 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     * 2. [可擴充] 根據設備清單進行 UI 顯示、狀態判斷、異常告警等
     *
     * @param system 發送方系統
     * @param topic  MQTT topic
     * @param ack    已反序列化的 S045AckPayload
     */
    @Override
    protected void process(String system, String topic, S045AckPayload ack) throws Exception {
        int deviceCount = ack.getMessage() != null && ack.getMessage().getSafetyDeviceList() != null
                ? ack.getMessage().getSafetyDeviceList().size() : 0;
        log.info("[S045] 收到安全 Sensor 狀態回覆 ACK：result={}, topic={}, system={}, deviceCount={}",
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

        // 2️⃣ [可擴充] 依裝置狀態清單進行顯示、告警、比對等業務
        // 目前僅保留回覆狀態後續處理入口，尚未實作異常或 UI 通知。
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S045"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S045";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S045AckPayload.class
     */
    @Override
    protected Class<S045AckPayload> getAckType() {
        return S045AckPayload.class;
    }
}
