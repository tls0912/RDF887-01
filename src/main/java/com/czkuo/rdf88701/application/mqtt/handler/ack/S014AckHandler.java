package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S014AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S014AckHandler
 * - 處理 CMD_ID=S014 的 ACK 訊息（零件預警清單回覆）
 * - 通常由我方 SAA 發送 S014 指令至 SEEC，由 SEEC 回覆 ACK
 * <p>
 * 功能：
 *   1. 將 ACK 訊息記錄至 mqtt_message_log（含 TOOL_LIST）
 *   2. 預留擴充處理，例如確認推播成功或分析 TOOL 使用狀況
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
public class S014AckHandler extends AbstractAckHandler<S014AckPayload> {

    private final MqttMessageLogService logService;

    public S014AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 S014 ACK 訊息
     *
     * @param system 發送方系統（例如 SEEC）
     * @param topic  MQTT topic（例如 seec_to_saa）
     * @param ack    已反序列化的 S014AckPayload 物件
     */
    @Override
    protected void process(String system, String topic, S014AckPayload ack) throws Exception {
        log.info("[S014] 收到零件預警清單 ACK：result={}, topic={}, system={}, toolCount={}",
                ack.getResult(),
                topic,
                system,
                ack.getMessage() != null && ack.getMessage().getToolList() != null
                        ? ack.getMessage().getToolList().size()
                        : 0
        );

        // 1️⃣ 將 payload 轉為 JsonNode 以利寫入資料庫
        JsonNode jsonPayload = objectMapper.valueToTree(ack);

        // 2️⃣ 記錄 ACK 訊息至 mqtt_message_log
        logService.record(
                topic,                          // MQTT topic
                system,                         // sender（對方系統）
                logService.getLocalSystem(),    // receiver（我方系統）
                jsonPayload,
                MqttMessageType.ACK             // 訊息類型為 ACK
        );

        // 3️⃣ [可擴充] 若 result=OK，後續可執行成功通知、比對清單等邏輯
        // 目前僅保留 TOOL_LIST 回覆處理入口，尚未實作 DB 寫入或警示比對。
    }

    /**
     * 回傳對應的 CMD_ID
     *
     * @return "S014"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S014";
    }

    /**
     * 回傳 payload 的資料型別（供 Jackson 反序列化）
     *
     * @return S014AckPayload.class
     */
    @Override
    protected Class<S014AckPayload> getAckType() {
        return S014AckPayload.class;
    }
}
