package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.L005AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.L005CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * L005CommandHandler
 * - 負責處理 CMD_ID=L005 的指令（WIP_Load 條碼檢查）
 * - 廠商發送條碼請求，ASE 判定是否允許入 STK
 * - 處理流程：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. [預留] 條碼解析與入庫判定邏輯
 *   3. 回傳 ACK（條碼解析結果與入庫可否）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class L005CommandHandler extends AbstractCommandHandler<L005CommandPayload> {

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
    public L005CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
    }

    /**
     * 處理收到的 L005 指令
     * <p>
     * 1. 記錄條碼檢查 COMMAND 至資料庫
     * 2. [預留] 執行條碼解析與入庫判斷
     * 3. 回覆 ACK
     *
     * @param system  來源系統（如廠商）
     * @param topic   MQTT topic
     * @param command 已反序列化的 L005CommandPayload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, L005CommandPayload command, MqttMessageType type) throws Exception {
        String barcode = (command.getMessage() != null) ? command.getMessage().getBarcode() : "";
        log.info("[L005] 收到條碼檢查請求指令：TID={}, topic={}, system={}, BARCODE={}",
                command.getTid(), topic, system, barcode);

        // 1️⃣ 記錄 COMMAND 至 mqtt_message_log
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                        // sender：對方系統
                systemContext.getSystemCode(), // receiver：本系統
                payload,
                MqttMessageType.COMMAND
        );

        // 2️⃣ [預留] 執行條碼檢查與入庫判斷
        // TODO: 條碼解析、查詢 DB、決定 PASS/FAIL

        // 3️⃣ 組建 ACK payload
        L005AckPayload ack = new L005AckPayload();
        ack.setCmd("LOAD");
        ack.setCmdId("L005");
        ack.setTid(command.getTid());
        ack.setIdDesc("BARCODE_CHECK_EVENT");

        L005AckPayload.Message ackMsg = new L005AckPayload.Message();
        ackMsg.setBarcode(barcode);
        // TODO: 實際解析填寫其他欄位（CARRIERID、LOT_ID、TRAY_HIGH、TRAY_TYPE、MESSAGE_TYPE）

        ack.setMessage(ackMsg);
        ack.setResult("PASS"); // 或 FAIL，解析失敗時請補原因
        ack.setResultMessage("");

        // 4️⃣ 發送 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "L005"
     */
    @Override
    protected String getCmdIdInternal() {
        return "L005";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return L005CommandPayload.class
     */
    @Override
    protected Class<L005CommandPayload> getCommandType() {
        return L005CommandPayload.class;
    }
}
