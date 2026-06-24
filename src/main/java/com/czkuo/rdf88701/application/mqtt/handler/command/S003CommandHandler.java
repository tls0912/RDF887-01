package com.czkuo.rdf88701.application.mqtt.handler.command;


import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S003AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S003CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S003CommandHandler
 * - 處理 CMD_ID=S003 的指令（系統初始化通知）
 * - 收到對方初始化請求後：
 *   1. 記錄原始 COMMAND 訊息
 *   2. 回傳 ACK（結果為 OK）
 *   3. 實際業務初始化處理可留待後續擴充
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S003CommandHandler extends AbstractCommandHandler<S003CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;

    public S003CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
    }

    /**
     * 處理 S003 指令（系統初始化請求）
     *
     * @param system  對方系統（如 ASE）
     * @param topic   MQTT topic（如 ase_to_saa）
     * @param command 已反序列化的 S003 payload
     * @param type    訊息類型（應為 COMMAND）
     */
    @Override
    protected void process(String system, String topic, S003CommandPayload command, MqttMessageType type) throws Exception {
        log.info("[S003] 收到系統初始化指令：TID={}, topic={}, system={}",
                command.getTid(),
                topic,
                system
        );

        // 我方系統代號
        String localSystem = systemContext.getSystemCode();

        // 1️⃣ 記錄原始指令訊息
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(topic, system, localSystem, payload, MqttMessageType.COMMAND);

        // 2️⃣ 建立 ACK 訊息（回覆 OK）
        S003AckPayload ack = new S003AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S003");
        ack.setTid(command.getTid());
        ack.setIdDesc("INITIAL_START");
        ack.setResult("OK");
        ack.setResultMessage("");

        // 3️⃣ 發送 ACK 回覆
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());

        // 4️⃣ [可擴充] 執行實際初始化邏輯（若有需要）
    }

    /**
     * 回傳對應的 CMD_ID（供 Router 快取註冊與識別）
     *
     * @return "S003"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S003";
    }

    /**
     * 回傳 payload 對應的物件型別（提供給 Jackson 進行反序列化）
     *
     * @return S003CommandPayload.class
     */
    @Override
    protected Class<S003CommandPayload> getCommandType() {
        return S003CommandPayload.class;
    }
}
