package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.U020AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * U020AckHandler
 * - 負責處理 CMD_ID=U020 的 ACK 訊息（Output WIP 架人員取貨回覆）
 * - 廠商回覆亮燈批號已處理、結束或失敗
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] UI 顯示、批號狀態處理、異常提示等
 */
@Slf4j
@Component
public class U020AckHandler extends AbstractAckHandler<U020AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /**
     * 建構子
     *
     * @param objectMapper JSON 處理器
     * @param logService   MQTT 訊息記錄服務
     */
    public U020AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 U020 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     * 2. [可擴充] 依照處理狀態呈現 UI、提示異常等
     *
     * @param system 發送方系統
     * @param topic  MQTT topic
     * @param ack    已反序列化的 U020AckPayload
     */
    @Override
    protected void process(String system, String topic, U020AckPayload ack) throws Exception {
        List<U020AckPayload.Message.LotInfo> lotList =
                (ack.getMessage() != null) ? ack.getMessage().getLotList() : null;
        int count = (lotList != null) ? lotList.size() : 0;
        log.info("[U020] 收到 Output WIP 架人員取貨回覆 ACK：result={}, 批號數={}, topic={}, system={}",
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

        // 2️⃣ [可擴充] 根據 result=OK/END/FAIL 處理 UI、例外狀態等
        // TODO: 若 FAIL 時顯示異常提示
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "U020"
     */
    @Override
    protected String getCmdIdInternal() {
        return "U020";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return U020AckPayload.class
     */
    @Override
    protected Class<U020AckPayload> getAckType() {
        return U020AckPayload.class;
    }
}
