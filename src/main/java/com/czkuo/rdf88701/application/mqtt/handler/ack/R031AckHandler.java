package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R031AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * R031AckHandler
 * - 負責處理 CMD_ID=R031 的 ACK 訊息（WIP→Manual Port 任務回覆）
 * - 廠商回覆 ASE 搬運任務的接收、進度、完成、失敗、取消等狀態
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] 根據狀態進行 UI 顯示、進度/異常提示
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
public class R031AckHandler extends AbstractAckHandler<R031AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    public R031AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 R031 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     * 2. [可擴充] 根據 result 狀態做 UI 顯示、任務進度/異常提示等
     */
    @Override
    protected void process(String system, String topic, R031AckPayload ack) throws Exception {
        R031AckPayload.Message msg = ack.getMessage();
        String lotId = (msg != null) ? msg.getLotId() : "";
        log.info("[R031] 收到 WIP→Manual Port 任務 ACK：result={}, LOT_ID={}, topic={}, system={}",
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

        // 2️⃣ [可擴充] 根據 result = OK/START/END/FAIL/CANCEL 處理 UI 顯示、異常等
        // 目前僅保留 ACK 後續處理入口，尚未實作任務進度或異常提示。
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     */
    @Override
    protected String getCmdIdInternal() {
        return "R031";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     */
    @Override
    protected Class<R031AckPayload> getAckType() {
        return R031AckPayload.class;
    }
}
