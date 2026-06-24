package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S067AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S067CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S067CommandHandler
 * - 負責處理 CMD_ID=S067 的指令（電池資訊查詢）
 * - ASE 發送查詢請求給廠商，要求回拋現有電池資訊
 * - 處理流程包含：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. [預留] 執行查詢業務
 *   3. 回傳 ACK（電池資訊清單）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S067CommandHandler extends AbstractCommandHandler<S067CommandPayload> {

    /** MQTT 訊息記錄服務：負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 系統識別（例如本系統代碼），由 context 提供，用於 record(receiver) 等 */
    private final SystemContext systemContext;

    /** Spring Event 發送器：內部會呼叫 publisher（MqttClientManager）送出 MQTT */
    private final MqttMessageEventPublisher responseEventPublisher;

    /** 來源與目標系統代號（本題明確要求寫死，禁止使用設定檔） */
    private static final String SOURCE_ASE  = "ase";
    private static final String TARGET_SEEC = "seec";

    /**
     * 建構子
     *
     * @param objectMapper           JSON 處理器
     * @param responseEventPublisher Spring Event 發送器
     * @param logService             MQTT 訊息記錄服務
     * @param systemContext          系統識別 context
     */
    public S067CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.responseEventPublisher = responseEventPublisher;
    }

    /**
     * 核心流程：
     *   1) 審計：把 ASE 送來的 COMMAND 落庫（topic/sender/receiver/payload/type）。
     *   2) 僅當來源為 ASE 時，才「原封不動」轉發到 SEEC（同一 TID/CMD_ID，不在此回 ACK）。
     *   3) 非 ASE 來源時，只做落庫，不轉發（避免誤轉與回圈）。
     */
    @Override
    protected void process(String system, String topic, S067CommandPayload command, MqttMessageType type) throws Exception {
        final String tid = command.getTid();
        final String from = system == null ? "" : system.trim().toLowerCase();

        log.info("[S067][COMMAND] 收到查詢請求：tid={}, from={}, topic={}", tid, from, topic);

        // 1) 先落庫「收到對方的 COMMAND」這一筆（sender=對方 system, receiver=我方系統代碼）
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,                         // 來源 topic（原封保留）
                system,                        // sender（對方實際的系統字串）
                systemContext.getSystemCode(), // receiver（本系統代碼）
                payload,
                MqttMessageType.COMMAND
        );

        // 2) 僅當「來源確實是 ASE」時才橋接到 SEEC；否則僅落庫不轉發
        if (SOURCE_ASE.equals(from)) {
            // 2-1) 再落庫一筆「橋接動作」（可用固定邏輯 topic 區分）
            logService.record(
                    "cmd/s067",     // 自定義：標示這是橋接出去的 CMD 記錄
                    SOURCE_ASE,     // sender：固定寫死 "ase"
                    TARGET_SEEC,    // receiver：固定寫死 "seec"
                    payload,
                    MqttMessageType.COMMAND
            );

            // 2-2) 轉發到 SEEC（payload 原封不動；同一 TID、同一 CMD_ID）
            String json = objectMapper.writeValueAsString(command);
            responseEventPublisher.publish(
                    TARGET_SEEC,     // 目標系統：seec
                    json,            // 完整 payload（字串）
                    MqttMessageType.COMMAND,
                    tid,             // 保持同一個 TID
                    "S067"           // CMD_ID
            );
            log.info("[S067][BRIDGE] ASE → SEEC（COMMAND）已轉發：tid={}", tid);
        } else {
            //log.debug("[S067][COMMAND] 非 ASE 來源，僅記錄不轉發：from={}", from);
        }
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S067"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S067";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S067CommandPayload.class
     */
    @Override
    protected Class<S067CommandPayload> getCommandType() {
        return S067CommandPayload.class;
    }
}
