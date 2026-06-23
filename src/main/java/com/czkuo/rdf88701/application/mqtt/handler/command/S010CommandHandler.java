package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.command.S010CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S010CommandHandler
 * - 處理 CMD_ID=S010 的 MQTT 指令（人員刷卡驗證請求）
 * - 通常由 SEEC 發送刷卡資訊至 SAA，請求驗證人員身分。
 * - 本處理器負責：
 *   1. 記錄收到的指令內容（COMMAND）至 mqtt_message_log
 *   2. [可擴充] 觸發後續驗證處理流程
 */
@Slf4j
@Component
public class S010CommandHandler extends AbstractCommandHandler<S010CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;

    public S010CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
    }

    /**
     * 主處理邏輯：
     * - 接收到 S010 指令後，記錄指令並可觸發驗證流程。
     *
     * @param system 發送來源系統（例如 seec）
     * @param topic  MQTT topic（如 seec-to-saa）
     * @param command 解析後的 payload
     * @param type 訊息類型（固定為 COMMAND）
     */
    @Override
    protected void process(String system, String topic, S010CommandPayload command, MqttMessageType type) throws Exception {
        log.info("[S010] 收到刷卡驗證請求，卡號={}，TID={}，來源系統={}",
                command.getMessage().getCardNumber(),
                command.getTid(),
                system
        );

        // 1️⃣ 將指令記錄至資料庫
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,                            // MQTT Topic（如 seec-to-saa）
                system,                           // sender（SEEC）
                systemContext.getSystemCode(),    // receiver（SAA）
                payload,
                MqttMessageType.COMMAND
        );

        // 2️⃣ [可擴充] 觸發驗證流程，並發送 S010 ACK（由其他服務處理）
    }

    /**
     * 回傳對應的 CMD_ID 字串（供 router 註冊與比對）
     *
     * @return 固定為 "S010"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S010";
    }

    /**
     * 回傳 payload 的實際型別（提供給反序列化使用）
     *
     * @return S010CommandPayload.class
     */
    @Override
    protected Class<S010CommandPayload> getCommandType() {
        return S010CommandPayload.class;
    }
}
