package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S011AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S011CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * S011CommandHandler
 * - 處理 CMD_ID=S011 的 MQTT 指令（人員觸發開啟安全門請求）
 * - 通常由 SAA 傳送，請求對方（ASE）驗證開門資格。
 * - 本處理器負責：
 *   1. 記錄收到的指令至資料庫
 *   2. [可擴充] 觸發實際驗證流程與安全控制處理
 */
@Slf4j
@Component
public class S011CommandHandler extends AbstractCommandHandler<S011CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;

    public S011CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
    }

    /**
     * 主處理流程：
     * - 接收 S011 指令後記錄訊息，並可擴充執行人員開門權限驗證
     *
     * @param system 來源系統（例如 seec）
     * @param topic MQTT Topic 名稱
     * @param command 解析後的 payload
     * @param type 指令類型（固定為 COMMAND）
     */
    @Override
    protected void process(String system, String topic, S011CommandPayload command, MqttMessageType type) throws Exception {
        log.info("[S011] 收到安全門開啟請求，TID={}, 來源系統={}", command.getTid(), system);

        // 1️⃣ 記錄收到的指令
        JsonNode jsonPayload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,
                systemContext.getSystemCode(),
                jsonPayload,
                MqttMessageType.COMMAND
        );

        // 2️⃣ 驗證邏輯（此處為模擬允許開門 + 提供人員）
        boolean allowOpen = false; // 模擬條件通過
        List<String> allowedStaff = List.of("E12345", "E23456"); // 模擬通過名單

        // 3️⃣ 建立 ACK 訊息
        S011AckPayload ack = new S011AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S011");
        ack.setTid(command.getTid());
        ack.setIdDesc("OPEN_DOOR_CHECK");
        ack.setResult(allowOpen ? "OK" : "NG");
        ack.setResultMessage(allowOpen ? "" : "Unauthorized request");

        // 填入 MESSAGE 區塊
        S011AckPayload.Message ackMessage = new S011AckPayload.Message();
        ackMessage.setStaffList(allowedStaff);
        ack.setMessage(ackMessage);

        // 發送 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /**
     * 回傳此 Handler 對應的指令代碼
     *
     * @return "S011"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S011";
    }

    /**
     * 指定反序列化對應的 payload 類別
     *
     * @return S011CommandPayload.class
     */
    @Override
    protected Class<S011CommandPayload> getCommandType() {
        return S011CommandPayload.class;
    }
}
