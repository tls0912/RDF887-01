package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R030AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.R030CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * R030CommandHandler
 * - 負責處理 CMD_ID=R030 的指令（E-Rack 搬貨至機台）
 * - SAA 傳送至 SEEC，啟動 AGV 移載任務
 * - 處理流程：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. [預留] 執行搬運指令與現場派工邏輯
 *   3. 回傳 ACK（回報狀態/分派結果）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
public class R030CommandHandler extends AbstractCommandHandler<R030CommandPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 系統識別（如 SAA/SEEC），由 context 提供 */
    private final SystemContext systemContext;

    public R030CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
    }

    /**
     * 處理收到的 R030 指令
     * <p>
     * 1. 記錄 COMMAND 至資料庫
     * 2. [預留] AGV 指令下發、派工等邏輯
     * 3. 回覆 ACK
     */
    @Override
    protected void process(String system, String topic, R030CommandPayload command, MqttMessageType type) throws Exception {
        R030CommandPayload.Message msg = command.getMessage();
        String lotId = (msg != null) ? msg.getLotId() : "";
        log.info("[R030] 收到 E-Rack 搬貨至機台指令：TID={}, topic={}, system={}, LOT_ID={}, EQP_PORT={}",
                command.getTid(), topic, system, lotId, (msg != null ? msg.getEqpPort() : null));

        // 1️⃣ 記錄 COMMAND 至 mqtt_message_log
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,
                systemContext.getSystemCode(),
                payload,
                MqttMessageType.COMMAND
        );

        // 2️⃣ [預留] 執行 AGV/現場派工邏輯
        // 目前僅保留後續處理入口，尚未實作額外流程。

        // 3️⃣ 組建 ACK payload
        R030AckPayload ack = new R030AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R030");
        ack.setTid(command.getTid());
        ack.setIdDesc("ROBOT_MOVE_SCH_FROM_ERACK_TO_EQP");

        R030AckPayload.Message ackMsg = new R030AckPayload.Message();
        if (msg != null) {
            ackMsg.setLotId(msg.getLotId());
            ackMsg.setCarrierId(msg.getCarrierId());
            ackMsg.setWipName(msg.getWipName());
            ackMsg.setDestLoc(msg.getDestLoc());
            ackMsg.setEqpPort(msg.getEqpPort());
            ackMsg.setDeviceName(msg.getDeviceName());
            ackMsg.setAgvSpeed(msg.getAgvSpeed());
            ackMsg.setRoboticArmSpeed(msg.getRoboticArmSpeed());
            ackMsg.setStkPort(msg.getStkPort());
        }
        ack.setMessage(ackMsg);

        ack.setResult("OK"); // OK/ASSIGN/START/END/FAIL/CANCEL 依場景業務調整
        ack.setResultMessage("");

        // 4️⃣ 發送 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     */
    @Override
    protected String getCmdIdInternal() {
        return "R030";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     */
    @Override
    protected Class<R030CommandPayload> getCommandType() {
        return R030CommandPayload.class;
    }
}
