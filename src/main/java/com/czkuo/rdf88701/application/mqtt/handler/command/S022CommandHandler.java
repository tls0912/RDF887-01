package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S022AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S022CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S022CommandHandler
 * - 負責處理 CMD_ID=S022 的指令（詢問系統控制狀態）
 * - ASE 主動向廠商詢問設備控制模式（REMOTE / LOCAL / MANUAL）
 * - 處理流程包含：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. [預留] 查詢設備控制狀態（連 PLC、DB 或 API 取狀態）
 *   3. 回傳 ACK（帶入設備名稱與控制狀態）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
public class S022CommandHandler extends AbstractCommandHandler<S022CommandPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 系統識別（如 SAA/SEEC），由 context 提供 */
    private final SystemContext systemContext;

    /**
     * 建構子
     *
     * @param objectMapper           JSON 處理器
     * @param responseEventPublisher Spring Event 發送器
     * @param logService             MQTT 訊息記錄服務
     * @param systemContext          系統識別 context
     */
    public S022CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
    }

    /**
     * 處理收到的 S022 指令
     * <p>
     * 1. 記錄詢問控制狀態 COMMAND 至資料庫
     * 2. [預留] 查詢設備控制狀態，組成 ACK payload
     * 3. 回覆 ACK
     *
     * @param system  來源系統（如 ASE）
     * @param topic   MQTT topic
     * @param command 已反序列化的 S022CommandPayload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, S022CommandPayload command, MqttMessageType type) throws Exception {
        // 1️⃣ 日誌顯示收到的查詢資訊
        log.info("[S022] 收到控制狀態查詢指令：TID={}, topic={}, system={}",
                command.getTid(),
                topic,
                system
        );

        // 2️⃣ 記錄 COMMAND 至 mqtt_message_log
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                                 // sender：對方系統
                systemContext.getSystemCode(),          // receiver：本系統
                payload,
                MqttMessageType.COMMAND
        );

        // 3️⃣ [預留] 查詢控制狀態，可由服務/PLC 取得
        // 目前僅保留後續處理入口，尚未實作額外流程。
        String deviceName = "STK";   // 範例，可動態取得
        String status = "REMOTE";      // 範例，可動態取得（REMOTE/LOCAL/MANUAL）

        // 4️⃣ 組建 ACK payload
        S022AckPayload ack = new S022AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S022");
        ack.setIdDesc("SYSTEM_CONTRAL_STATUS");
        ack.setTid(command.getTid());
        ack.setResult("OK");
        ack.setResultMessage("");

        S022AckPayload.Message ackMsg = new S022AckPayload.Message();
        ackMsg.setDeviceName(deviceName);
        ackMsg.setStatus(status);
        ack.setMessage(ackMsg);

        // 5️⃣ 發送 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S022"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S022";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S022CommandPayload.class
     */
    @Override
    protected Class<S022CommandPayload> getCommandType() {
        return S022CommandPayload.class;
    }
}
