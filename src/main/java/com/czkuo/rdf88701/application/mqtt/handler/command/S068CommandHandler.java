package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S068AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S068CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S068CommandHandler
 * - 負責處理 CMD_ID=S068 的指令（打帶前狀態確認）
 * - 廠商主動傳送至 ASE，請求確認當前設備是否允許打帶
 * - 處理流程：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. [預留] 執行允許判斷邏輯
 *   3. 回傳 ACK（允許或拒絕）
 */
@Slf4j
@Component
public class S068CommandHandler extends AbstractCommandHandler<S068CommandPayload> {

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
    public S068CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
    }

    /**
     * 處理收到的 S068 指令
     * <p>
     * 1. 記錄打帶前狀態確認 COMMAND 至資料庫
     * 2. [預留] 執行允許打帶業務邏輯
     * 3. 回覆 ACK
     *
     * @param system  來源系統（如廠商）
     * @param topic   MQTT topic
     * @param command 已反序列化的 S068CommandPayload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, S068CommandPayload command, MqttMessageType type) throws Exception {
        log.info("[S068] 收到打帶前狀態確認指令：TID={}, topic={}, system={}",
                command.getTid(), topic, system);

        // 1️⃣ 記錄 COMMAND 至 mqtt_message_log
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                        // sender：對方系統
                systemContext.getSystemCode(), // receiver：本系統
                payload,
                MqttMessageType.COMMAND
        );

        // 2️⃣ [預留] 執行允許打帶業務邏輯（例：查詢設備目前狀態）
        // TODO: 判斷當前是否允許打帶
        String allowResult = "OK"; // 或 "NG"
        String resultMessage = ""; // 若 NG 時可寫明原因

        // 3️⃣ 組建 ACK payload
        S068AckPayload ack = new S068AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S068");
        ack.setTid(command.getTid());
        ack.setIdDesc("TAPING_MACHINE_CHECK");
        ack.setResult(allowResult);
        ack.setResultMessage(resultMessage);

        // 4️⃣ 發送 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S068"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S068";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S068CommandPayload.class
     */
    @Override
    protected Class<S068CommandPayload> getCommandType() {
        return S068CommandPayload.class;
    }
}
