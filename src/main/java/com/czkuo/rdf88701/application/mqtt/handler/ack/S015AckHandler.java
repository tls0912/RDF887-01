package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S015AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S015AckHandler
 * - 負責處理 CMD_ID=S015 的 ACK 訊息（零件預警設定回覆）
 * - 一般在 SAA 發送 S015 指令後，會收到 SEEC 回覆的 ACK
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] 根據執行結果做進一步通知或異常補救
 */
@Slf4j
@Component
public class S015AckHandler extends AbstractAckHandler<S015AckPayload> {

    /** 封裝 MQTT 訊息記錄邏輯的服務（寫入 mqtt_message_log） */
    private final MqttMessageLogService logService;

    /**
     * 建構子
     *
     * @param objectMapper JSON 處理器
     * @param logService   MQTT 訊息記錄服務
     */
    public S015AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 S015 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     * 2. [可擴充] 根據結果處理通知、異常流程等
     *
     * @param system 發送方系統（如 SEEC）
     * @param topic  MQTT topic（如 seec_to_saa）
     * @param ack    已反序列化的 S015AckPayload 物件
     */
    @Override
    protected void process(String system, String topic, S015AckPayload ack) throws Exception {
        // 1️⃣ 日誌顯示回覆資訊
        log.info("[S015] 收到零件預警設定 ACK：result={}, topic={}, system={}",
                ack.getResult(),
                topic,
                system
        );

        // 2️⃣ 記錄 ACK 訊息至 mqtt_message_log
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        logService.record(
                topic,                          // MQTT topic
                system,                         // sender（對方系統）
                logService.getLocalSystem(),    // receiver（我方系統）
                jsonPayload,
                MqttMessageType.ACK
        );

        // 3️⃣ [可擴充] 若 result=FAIL，可進行失敗處理/警示通知
        // TODO: 根據 result 做額外異常流程
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S015"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S015";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S015AckPayload.class
     */
    @Override
    protected Class<S015AckPayload> getAckType() {
        return S015AckPayload.class;
    }
}
