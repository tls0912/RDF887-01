package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttConnectionService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S002AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S002CommandPayload;
import com.czkuo.rdf88701.infra.mqtt.ReplyOnceValve;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S002CommandHandler
 * - 處理 CMD_ID=S002 的指令（心跳訊息）
 * - 收到對方發出的心跳指令後：
 *   1. 記錄原始訊息（COMMAND 類型）
 *   2. 回傳 ACK（內容為 OK）
 *   3. 若對方為已連線狀態，更新其 lastHeartbeatTime
 *
 * 防回聲迴圈：
 *   - 透過 ReplyOnceValve：同一 sender+CMD_ID+TID 在 TTL 內只允許回覆一次
 *   - 第二次之後仍會記錄與刷新心跳，但不再回覆 ACK
 */
@Slf4j
@Component
public class S002CommandHandler extends AbstractCommandHandler<S002CommandPayload> {

    private static final String CMD_ID = "S002";

    private final MqttMessageLogService logService;
    private final MqttConnectionService connectionService;
    private final SystemContext systemContext;
    private final ReplyOnceValve replyOnceValve;

    /**
     * 建構子注入必要元件
     *
     * @param objectMapper           JSON 處理器
     * @param responseEventPublisher 訊息回應發送器（封裝 Spring Event）
     * @param logService             用於寫入 mqtt_message_log
     * @param connectionService      用於更新心跳時間與連線狀態
     * @param systemContext          提供我方系統代碼（如 saa）
     * @param replyOnceValve         一次回覆閥門（防止回聲迴圈）
     */
    public S002CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              MqttConnectionService connectionService,
                              SystemContext systemContext,
                              ReplyOnceValve replyOnceValve) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.connectionService = connectionService;
        this.systemContext = systemContext;
        this.replyOnceValve = replyOnceValve;
    }

    /**
     * 處理 S002 指令（心跳）
     *
     * @param system  對方系統（例如 ase / seec）
     * @param topic   MQTT topic（例如 seec_to_saa）
     * @param command 已反序列化的 payload（心跳內容）
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, S002CommandPayload command, MqttMessageType type) throws Exception {
        log.info("[S002] 收到心跳指令：TID={}, topic={}, system={}",
                command.getTid(), topic, system);

        // 我方系統代碼
        String localSystem = systemContext.getSystemCode();

        // 1️⃣ 記錄原始 COMMAND 訊息（進入 mqtt_message_log）
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,          // 對方系統（sender）
                localSystem,     // 我方系統（receiver）
                payload,
                MqttMessageType.COMMAND
        );

        // 2️⃣ 更新心跳時間（若對方已連線）
        connectionService.refreshHeartbeat(system);

        // 2.5️⃣ 回覆閥門：同一 sender+CMD_ID+TID 在 TTL 內只允許回覆一次
        //      - 若返回 false：代表在 TTL 內已回覆過，避免回聲迴圈 → 不再回 ACK
        String tid = command.getTid();
        if (!replyOnceValve.shouldReplyOnce(system, CMD_ID, tid)) {
            log.info("[S002] 回覆已在 TTL 內發送過，跳過 ACK（system={}, tid={}）", system, tid);
            return;
        }

        // 3️⃣ 建立 ACK 訊息（回覆格式不可動）
        S002AckPayload ack = new S002AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId(CMD_ID);
        ack.setIdDesc("CHECK_READY");
        ack.setTid(tid);
        ack.setResult("OK");
        ack.setResultMessage("");

        // 4️⃣ 發送 ACK（由我方回給對方）
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /**
     * 回傳對應的 CMD_ID（供 Router 快取註冊與識別）
     *
     * @return "S002"
     */
    @Override
    protected String getCmdIdInternal() {
        return CMD_ID;
    }

    /**
     * 回傳 payload 對應的物件型別（提供給 Jackson 進行反序列化）
     *
     * @return S002CommandPayload.class
     */
    @Override
    protected Class<S002CommandPayload> getCommandType() {
        return S002CommandPayload.class;
    }
}
