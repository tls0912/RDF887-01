package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S004AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S004AckHandler
 * - 負責處理 CMD_ID=S004 的 ACK 訊息（對應 WIP 查詢的回覆）。
 * - 一般由我方發出 S004 指令後，對方系統回覆儲格狀態清單，進入此流程。
 *<p>
 * 處理內容包含：
 *  1. 記錄 ACK 訊息至 mqtt_message_log
 *  2. 可依據回應資料進行業務邏輯處理（目前預留）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S004AckHandler extends AbstractAckHandler<S004AckPayload> {

    private final MqttMessageLogService logService;

    /**
     * 建構子
     *
     * @param objectMapper JSON 處理器（用於序列化/反序列化與轉換 JsonNode）
     * @param logService   MQTT 訊息記錄服務，負責寫入 mqtt_message_log
     */
    public S004AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 實際執行 ACK 訊息的處理邏輯。
     * <p>
     * 1. 印出收到的儲格狀態資料
     * 2. 轉換 payload 為 JsonNode
     * 3️. 寫入 mqtt_message_log
     *
     * @param system 對方系統（如 ase）
     * @param topic  MQTT topic（來源 topic，例如 ase_to_saa）
     * @param ack    已反序列化的 ACK payload（包含儲格資料）
     */
    @Override
    protected void process(String system, String topic, S004AckPayload ack) throws Exception {
        // 1️⃣ 印出回傳資訊摘要（如儲格筆數）
        int size = ack.getMessage() != null && ack.getMessage().getData() != null
                ? ack.getMessage().getData().size()
                : 0;

        log.info("[S004] 收到儲格狀態 ACK：TID={}, 儲格數量={}, topic={}, system={}",
                ack.getTid(),
                size,
                topic,
                system
        );

        // 2️⃣ 將 payload 轉為 JsonNode
        JsonNode jsonPayload = objectMapper.valueToTree(ack);

        // 3️⃣ 記錄至 mqtt_message_log
        logService.record(
                topic,                          // MQTT topic
                system,                         // 發送方（對方）
                logService.getLocalSystem(),    // 接收方（我方）
                jsonPayload,                    // JSON payload
                MqttMessageType.ACK             // 訊息類型
        );

        // [預留] 可根據回應內容執行後續業務邏輯
    }

    /**
     * 回傳支援的 CMD_ID，供系統註冊與 dispatch 判斷。
     *
     * @return "S004"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S004";
    }

    /**
     * 回傳 payload 類別（用於 Jackson 反序列化）
     *
     * @return S004AckPayload.class
     */
    @Override
    protected Class<S004AckPayload> getAckType() {
        return S004AckPayload.class;
    }
}
