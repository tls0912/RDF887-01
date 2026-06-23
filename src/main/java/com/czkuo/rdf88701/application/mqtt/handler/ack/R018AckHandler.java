package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R018AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * R018AckHandler
 * - 負責處理 CMD_ID=R018 的 ACK 訊息（刪除任務回覆）
 * - 用於 SAA→ASE、SEEC→SAA 等任務刪除場景
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] 根據回覆內容執行 UI 顯示、任務流轉、異常提示等
 */
@Slf4j
@Component
public class R018AckHandler extends AbstractAckHandler<R018AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /**
     * 建構子
     *
     * @param objectMapper JSON 處理器
     * @param logService   MQTT 訊息記錄服務
     */
    public R018AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 R018 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     * 2. [可擴充] 根據結果 UI 呈現、異常提示等
     *
     * @param system 發送方系統
     * @param topic  MQTT topic
     * @param ack    已反序列化的 R018AckPayload
     */
    @Override
    protected void process(String system, String topic, R018AckPayload ack) throws Exception {
        R018AckPayload.Message msg = ack.getMessage();
        String cmdTid = (msg != null) ? msg.getCmdTid() : "";
        log.info("[R018] 收到刪除任務 ACK：result={}, CMD_TID={}, topic={}, system={}",
                ack.getResult(), cmdTid, topic, system);

        // 1️⃣ 記錄 ACK 至 mqtt_message_log
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        logService.record(
                topic,                        // MQTT topic
                system,                       // sender（對方系統）
                logService.getLocalSystem(),  // receiver（我方系統）
                jsonPayload,
                MqttMessageType.ACK
        );

        // 2️⃣ [可擴充] 根據 result=OK/FAIL 做異常處理或 UI 提示
        // TODO: 失敗時顯示異常，成功時可通知前端更新
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "R018"
     */
    @Override
    protected String getCmdIdInternal() {
        return "R018";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return R018AckPayload.class
     */
    @Override
    protected Class<R018AckPayload> getAckType() {
        return R018AckPayload.class;
    }
}
