package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S021AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * S021AckHandler
 * - 負責處理 CMD_ID=S021 的 ACK 訊息（設備狀態查詢回覆）
 * - 廠商回覆設備名稱與目前狀態（IDLE/RUN/ERROR 等）
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] 根據設備狀態進行後續流程或異常通知
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S021AckHandler extends AbstractAckHandler<S021AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /**
     * 建構子
     *
     * @param objectMapper JSON 處理器
     * @param logService   MQTT 訊息記錄服務
     */
    public S021AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 S021 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     *
     * @param system 發送方系統
     * @param topic  MQTT topic
     * @param ack    已反序列化的 S021AckPayload
     */
    @Override
    protected void process(String system, String topic, S021AckPayload ack) throws Exception {
        // 1) 逐筆設備狀態列印（MESSAGE 為 List）
        List<S021AckPayload.Message> items =
                ack.getMessage() != null ? ack.getMessage() : Collections.emptyList();

        int errorCount = 0;
        if (items.isEmpty()) {
            log.warn("[S021] 收到 ACK 但 MESSAGE 為空：result={}, topic={}, system={}, tid={}, desc={}",
                    ack.getResult(), topic, system, ack.getTid(), ack.getIdDesc());
        } else {
            for (S021AckPayload.Message m : items) {
                if (m == null) continue;
                String dev = safe(m.getDeviceName());
                String st  = safe(m.getStatus());
                log.info("[S021] ACK 狀態：deviceName={}, status={}, topic={}, system={}, tid={}",
                        dev, st, topic, system, ack.getTid());

            }
        }

        // 2) 記錄 ACK 至 mqtt_message_log（整包）
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        logService.record(
                topic,                       // topic
                system,                      // sender（對方系統）
                logService.getLocalSystem(), // receiver（我方系統）
                jsonPayload,
                MqttMessageType.ACK
        );

        // 3) 彙總列印
        log.info("[S021] ACK 完成：count={}, errors={}, result={}, resultMessage={}, tid={}",
                items.size(), errorCount, safe(ack.getResult()), safe(ack.getResultMessage()), ack.getTid());
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S021"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S021";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S021AckPayload.class
     */
    @Override
    protected Class<S021AckPayload> getAckType() {
        return S021AckPayload.class;
    }
}
