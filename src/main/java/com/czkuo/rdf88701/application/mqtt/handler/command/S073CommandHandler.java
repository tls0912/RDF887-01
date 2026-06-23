package com.czkuo.rdf88701.application.mqtt.handler.command;


import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S073AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S073CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S073CommandHandler
 * - 負責處理 CMD_ID=S073 的指令（Tray 拆併前資訊確認）
 * - 廠商傳送 Tray 拍攝圖像與資訊，ASE 判斷是否允許拆併
 * - 處理流程：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. [預留] 圖像分析或業務判斷
 *   3. 回傳 ACK（允許/拒絕結果）
 */
@Slf4j
@Component
public class S073CommandHandler extends AbstractCommandHandler<S073CommandPayload> {

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
    public S073CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
    }

    /**
     * 處理收到的 S073 指令
     * <p>
     * 1. 記錄 Tray 拆併前資訊 COMMAND 至資料庫
     * 2. [預留] 執行圖像或業務判斷邏輯
     * 3. 回覆 ACK
     *
     * @param system  來源系統（如廠商）
     * @param topic   MQTT topic
     * @param command 已反序列化的 S073CommandPayload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, S073CommandPayload command, MqttMessageType type) throws Exception {
        String lotId = (command.getMessage() != null) ? command.getMessage().getLotId() : "";
        log.info("[S073] 收到拆併前Tray資訊確認指令：TID={}, topic={}, system={}, LOT_ID={}",
                command.getTid(), topic, system, lotId);

        // 1️⃣ 記錄 COMMAND 至 mqtt_message_log
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                        // sender：對方系統
                systemContext.getSystemCode(), // receiver：本系統
                payload,
                MqttMessageType.COMMAND
        );

        // 2️⃣ [預留] 執行 Tray 圖像/資訊判斷
        // TODO: 圖像分析或欄位驗證，如有異常可回覆 NG

        // 3️⃣ 組建 ACK payload
        S073AckPayload ack = new S073AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S073");
        ack.setTid(command.getTid());
        ack.setIdDesc("TRAY_OCR_CHECK_INFO");
        ack.setResult("OK");            // 或 NG，異常時請填入錯誤
        ack.setResultMessage("");

        // 4️⃣ 發送 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S073"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S073";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S073CommandPayload.class
     */
    @Override
    protected Class<S073CommandPayload> getCommandType() {
        return S073CommandPayload.class;
    }
}
