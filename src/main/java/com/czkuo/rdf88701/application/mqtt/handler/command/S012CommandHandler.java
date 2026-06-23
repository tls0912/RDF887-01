package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S012AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S012CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * S012CommandHandler
 * - 處理 CMD_ID=S012 的指令（關閉安全門請求）
 * - 收到對方（SEEC）關門請求後：
 *   1. 記錄原始 COMMAND 訊息
 *   2. 回傳 ACK（包含是否允許與通過認證的人員清單）
 *   3. 實際授權邏輯可於後續擴充處理
 */
@Slf4j
@Component
public class S012CommandHandler extends AbstractCommandHandler<S012CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;

    public S012CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
    }

    /**
     * 處理 S012 指令（關閉安全門驗證請求）
     *
     * @param system  發送方系統（如 SEEC）
     * @param topic   MQTT topic（如 seec_to_saa）
     * @param command 已反序列化的 payload
     * @param type    訊息類型（應為 COMMAND）
     */
    @Override
    protected void process(String system, String topic, S012CommandPayload command, MqttMessageType type) throws Exception {
        log.info("[S012] 收到關門驗證指令：TID={}, topic={}, system={}",
                command.getTid(),
                topic,
                system
        );

        // 我方系統代號
        String localSystem = systemContext.getSystemCode();

        // 1️⃣ 記錄原始指令內容至資料庫
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(topic, system, localSystem, payload, MqttMessageType.COMMAND);

        boolean allowOpen = false; // 模擬條件通過

        // 2️⃣ 建立 ACK 回覆物件（預設為允許，實際可根據人員驗證結果決定）
        S012AckPayload ack = new S012AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S012");
        ack.setTid(command.getTid());
        ack.setIdDesc("CLOSE_DOOR_CHECK");
        ack.setResult(allowOpen ? "OK" : "NG");
        ack.setResultMessage("");

        // 建立回傳的 STAFF_LIST（實務上應從授權系統查詢）
        S012AckPayload.Message message = new S012AckPayload.Message();
        message.setStaffList(Collections.singletonList("E123456")); // TODO: 改為實際驗證結果

        ack.setMessage(message);

        // 3️⃣ 發送 ACK 回覆
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());

        // 4️⃣ [可擴充] 驗證授權邏輯與記錄其他系統資訊
    }

    /**
     * 回傳對應的 CMD_ID（供 Router 快取註冊與支援判斷）
     *
     * @return "S012"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S012";
    }

    /**
     * 回傳 payload 對應的物件型別（提供給 Jackson 反序列化）
     *
     * @return S012CommandPayload.class
     */
    @Override
    protected Class<S012CommandPayload> getCommandType() {
        return S012CommandPayload.class;
    }
}
