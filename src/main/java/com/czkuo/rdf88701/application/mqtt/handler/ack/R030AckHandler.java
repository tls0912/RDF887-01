package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R030AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * R030AckHandler
 * - 負責處理 CMD_ID=R030 的 ACK 訊息（E-Rack 搬貨至機台回覆）
 * - SEEC 回報 AGV 任務處理狀態、執行進度、異常或錯誤
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] 根據結果狀態處理 UI 顯示、異常提示、進度控制
 */
@Slf4j
@Component
public class R030AckHandler extends AbstractAckHandler<R030AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    public R030AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 R030 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     * 2. [可擴充] 根據 result 狀態做 UI 呈現、任務進度/異常提示等
     */
    @Override
    protected void process(String system, String topic, R030AckPayload ack) throws Exception {
        R030AckPayload.Message msg = ack.getMessage();
        String lotId = (msg != null) ? msg.getLotId() : "";
        log.info("[R030] 收到 E-Rack 搬貨至機台任務 ACK：result={}, LOT_ID={}, topic={}, system={}",
                ack.getResult(), lotId, topic, system);

        // 1️⃣ 記錄 ACK 至 mqtt_message_log
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        logService.record(
                topic,
                system,
                logService.getLocalSystem(),
                jsonPayload,
                MqttMessageType.ACK
        );

        // 2️⃣ [可擴充] 根據 result = OK/ASSIGN/START/END/FAIL/CANCEL 做 UI 顯示、異常提示等
        // TODO: 任務進度處理/異常顯示
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     */
    @Override
    protected String getCmdIdInternal() {
        return "R030";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     */
    @Override
    protected Class<R030AckPayload> getAckType() {
        return R030AckPayload.class;
    }
}
