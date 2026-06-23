package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S003AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S003AckHandler
 * - 負責處理 CMD_ID=S003 的 ACK 訊息（對應系統初始化請求的回覆）。
 * - 一般由我方發出 S003 指令，對方系統回覆後進入此流程。
 * - 處理內容：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. 可後續擴充其他處理（如初始化成功後續流程）
 */
@Slf4j
@Component
public class S003AckHandler extends AbstractAckHandler<S003AckPayload> {

    private final MqttMessageLogService logService;

    public S003AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 S003 ACK 回覆
     *
     * @param system 發送方系統（如 ase）
     * @param topic  MQTT topic（如 ase_to_saa）
     * @param ack    已解析的 S003 ACK payload
     */
    @Override
    protected void process(String system, String topic, S003AckPayload ack) throws Exception {
        log.info("[S003] 收到初始化 ACK：result={}, topic={}, system={}",
                ack.getResult(),
                topic,
                system
        );

        JsonNode jsonPayload = objectMapper.valueToTree(ack);

        logService.record(
                topic,                          // MQTT topic（來源）
                system,                         // sender（對方）
                logService.getLocalSystem(),    // receiver（我方）
                jsonPayload,
                MqttMessageType.ACK
        );

        // [可擴充] 若 ACK = OK，可繼續下一階段業務邏輯
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 快取註冊與支援判斷。
     *
     * @return "S003"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S003";
    }

    /**
     * 回傳 payload 對應的物件型別（提供給 Jackson 用於反序列化）。
     *
     * @return S003AckPayload.class
     */
    @Override
    protected Class<S003AckPayload> getAckType() {
        return S003AckPayload.class;
    }
}
