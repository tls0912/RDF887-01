package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S019AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S019AckHandler
 * - 負責處理 CMD_ID=S019 的 ACK 訊息（Robot HMI 訊息顯示結果回覆）
 * - 一般於 ASE 發送顯示訊息後，由廠商系統回覆
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] 根據結果做通知、顯示確認或異常補救
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
public class S019AckHandler extends AbstractAckHandler<S019AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /**
     * 建構子
     *
     * @param objectMapper JSON 處理器
     * @param logService   MQTT 訊息記錄服務
     */
    public S019AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 S019 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     * 2. [可擴充] 根據處理結果（OK/FAIL）觸發通知或異常機制
     *
     * @param system 發送方系統（如對方 HMI 控制端）
     * @param topic  MQTT topic
     * @param ack    已反序列化的 S019AckPayload
     */
    @Override
    protected void process(String system, String topic, S019AckPayload ack) throws Exception {
        // 1️⃣ 日誌顯示收到的訊息內容
        log.info("[S019] 收到 Robot HMI 訊息顯示 ACK：result={}, topic={}, system={}, msgEn={}, msgCh={}",
                ack.getResult(),
                topic,
                system,
                ack.getMessage() != null ? ack.getMessage().getMsgEn() : "",
                ack.getMessage() != null ? ack.getMessage().getMsgCh() : ""
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

        // 3️⃣ [可擴充] 根據 result 處理通知或異常補救
        // 目前僅保留 FAIL 後續處理入口，尚未實作異常流程。
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S019"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S019";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S019AckPayload.class
     */
    @Override
    protected Class<S019AckPayload> getAckType() {
        return S019AckPayload.class;
    }
}
