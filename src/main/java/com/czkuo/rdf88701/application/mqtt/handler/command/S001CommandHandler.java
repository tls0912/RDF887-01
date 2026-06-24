package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttConnectionService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S001AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S001CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


/**
 * S001CommandHandler
 * - 處理 CMD_ID=S001 的指令（建立連線）
 * - 收到對方建立連線請求後：
 *   1. 記錄原始訊息（COMMAND）
 *   2. 回傳 ACK（成功資訊與我方版本資訊）
 *   3. 標記對方為 connected 並更新連線狀態與 log
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S001CommandHandler extends AbstractCommandHandler<S001CommandPayload> {

    private final MqttMessageLogService logService;
    private final MqttConnectionService connectionService;
    private final SystemContext systemContext;

    /**
     * 建構子注入必要元件
     *
     * @param objectMapper           JSON 處理器
     * @param responseEventPublisher 訊息回應發送器（封裝 Spring Event）
     * @param logService             用於寫入 mqtt_message_log
     * @param connectionService      用於維護連線狀態與日誌
     * @param systemContext          提供我方系統代碼（例如 saa）
     */
    public S001CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              MqttConnectionService connectionService,
                              SystemContext systemContext) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.connectionService = connectionService;
        this.systemContext = systemContext;
    }

    /**
     * 處理 S001 指令（建立連線）
     *
     * @param system  對方系統（ase/seec）
     * @param topic   MQTT topic（例：ase_to_saa）
     * @param command 已反序列化的 payload
     * @param type    訊息類型（應為 COMMAND）
     */
    @Override
    protected void process(String system, String topic, S001CommandPayload command, MqttMessageType type) throws Exception {
        log.info("[S001] 建立連線請求：program={}, version={}, topic={}, system={}",
                command.getMessage().getProgramName(),
                command.getMessage().getVersion(),
                topic,
                system
        );

        // 我方代號
        String sender = systemContext.getSystemCode();

        // 1️⃣ 記錄原始訊息
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,         // 對方系統
                sender,         // 我方系統（由 SystemContext 提供）
                payload,
                MqttMessageType.COMMAND
        );

        // 2️⃣ 標記連線狀態為 connected 並記錄日誌
        connectionService.markConnected(system, "收到 S001 建立連線請求");

        // 3️⃣ 建立 ACK 訊息
        S001AckPayload ack = new S001AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S001");
        ack.setTid(command.getTid());
        ack.setIdDesc("PC_LINK");
        ack.setResult("OK");
        ack.setResultMessage("");

        S001AckPayload.Message ackMessage = new S001AckPayload.Message();
        ackMessage.setProgramName("RDF887-01");
        ackMessage.setVersion("1.0.0");
        ackMessage.setHint("SAA-ENV-PROD");
        ack.setMessage(ackMessage);

        // 4️⃣ 發送 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 快取註冊與支援判斷。
     *
     * @return "S001"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S001";
    }

    /**
     * 回傳 payload 對應的物件型別（提供給 Jackson 用於反序列化）。
     *
     * @return S001CommandPayload.class
     */
    @Override
    protected Class<S001CommandPayload> getCommandType() {
        return S001CommandPayload.class;
    }
}
