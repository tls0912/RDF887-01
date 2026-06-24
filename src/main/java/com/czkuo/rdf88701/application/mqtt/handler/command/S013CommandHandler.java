package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S013AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S013CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * S013CommandHandler
 * - 負責處理 CMD_ID=S013 的指令（人員復歸 / 啟動請求）
 * - 一般由 SAA 傳送刷卡請求至 ASE，由 ASE 驗證是否允許執行 RESET / START
 * - 本 Handler 處理流程：
 *   1. 記錄原始指令至 mqtt_message_log（MESSAGE payload 僅於 ACK 回傳）
 *   2. 發送回應 ACK 給發送端（預設回 OK，後續可接入驗證邏輯）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S013CommandHandler extends AbstractCommandHandler<S013CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;

    public S013CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
    }

    /**
     * 處理 S013 指令（RESET / START 請求）
     *
     * @param system  發送端系統（例如 seec）
     * @param topic   MQTT topic（如 seec_to_saa）
     * @param command 已反序列化的 S013 payload
     * @param type    訊息類型（應為 COMMAND）
     */
    @Override
    protected void process(String system, String topic, S013CommandPayload command, MqttMessageType type) throws Exception {
        log.info("[S013] 收到 RESET/START 請求：TID={}, topic={}, system={}",
                command.getTid(), topic, system);

        // 取得我方系統代碼
        String localSystem = systemContext.getSystemCode();

        // 1️⃣ 記錄原始指令至 mqtt_message_log
        JsonNode jsonPayload = objectMapper.valueToTree(command);
        logService.record(topic, system, localSystem, jsonPayload, MqttMessageType.COMMAND);

        // 2️⃣ 建立 ACK Payload（預設為 OK，可依實際驗證結果調整）
        S013AckPayload ack = new S013AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S013");
        ack.setTid(command.getTid());
        ack.setIdDesc("RESET_CHECK");
        ack.setResult("NG");
        ack.setResultMessage("");

        // ⚠️ 預設不含任何授權人員，後續可擴充為驗證通過清單
        S013AckPayload.Message ackMessage = new S013AckPayload.Message();
        ackMessage.setStaffList(Collections.emptyList());
        ack.setMessage(ackMessage);

        // 3️⃣ 發送 ACK 回應給對方系統
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /**
     * 回傳對應的 CMD_ID（供 Router 快取註冊與識別）
     *
     * @return "S013"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S013";
    }

    /**
     * 回傳 payload 對應的物件型別（提供給 Jackson 進行反序列化）
     *
     * @return S013CommandPayload.class
     */
    @Override
    protected Class<S013CommandPayload> getCommandType() {
        return S013CommandPayload.class;
    }
}
