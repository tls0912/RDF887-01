package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S067AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S067AckHandler
 * - 負責處理 CMD_ID=S067 的 ACK 訊息（電池資訊回拋結果）
 * - 廠商回拋即時電池資訊清單
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] 解析電池清單、顯示、儲存、狀態異常通知等
 */
@Slf4j
@Component
public class S067AckHandler extends AbstractAckHandler<S067AckPayload> {

    /** MQTT 訊息記錄服務：負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** Spring Event 發送器：用於把 ACK 轉發回 ASE */
    private final MqttMessageEventPublisher responseEventPublisher;

    /** 來源與目標系統代號（本題明確要求寫死，禁止使用設定檔） */
    private static final String SOURCE_SEEC = "seec";
    private static final String TARGET_ASE  = "ase";

    /**
     * 建構子
     *
     * @param objectMapper             Jackson 物件對映器（父類別也會持有）
     * @param logService               訊息落庫服務
     * @param responseEventPublisher   Spring 事件發送器（內部轉 MQTT）
     */
    public S067AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService,
                          MqttMessageEventPublisher responseEventPublisher) {
        super(objectMapper);
        this.logService = logService;
        this.responseEventPublisher = responseEventPublisher;
    }

    /**
     * 核心流程：
     *   1) 審計：把 SEEC 回來的 ACK 落庫。
     *   2) 僅當來源為 SEEC 時，才「原封不動」轉發到 ASE（同一 TID/CMD_ID）。
     *   3) 非 SEEC 來源時，只做落庫，不轉發（避免誤轉與回圈）。
     */
    @Override
    protected void process(String system, String topic, S067AckPayload ack) throws Exception {
        final String tid = ack.getTid();
        final String from = system == null ? "" : system.trim().toLowerCase();

        final int batteryCount =
                (ack.getMessage() != null && ack.getMessage().getBatteryList() != null)
                        ? ack.getMessage().getBatteryList().size()
                        : 0;

        log.info("[S067][ACK] 收到回拋：result={}, batteries={}, from={}, topic={}, tid={}",
                ack.getResult(), batteryCount, from, topic, tid);

        // 1) 先落庫「收到對方的 ACK」這一筆（sender=對方 system, receiver=本地系統）
        JsonNode json = objectMapper.valueToTree(ack);
        logService.record(
                topic,                       // 來源 topic（原封保留）
                system,                      // sender（對方實際的系統字串）
                logService.getLocalSystem(), // receiver（本系統代碼）
                json,
                MqttMessageType.ACK
        );

        // 2) 僅當「來源確實是 SEEC」時才橋接到 ASE；否則僅落庫不轉發
        if (SOURCE_SEEC.equals(from)) {
            // 2-1) 再落庫一筆「橋接動作」（可用固定邏輯 topic 區分）
            logService.record(
                    "ack/s067",   // 自定義：標示這是橋接出去的 ACK 記錄
                    SOURCE_SEEC,  // sender：固定寫死 "seec"
                    TARGET_ASE,   // receiver：固定寫死 "ase"
                    json,
                    MqttMessageType.ACK
            );

            // 2-2) 轉發到 ASE（payload 原封不動；同一 TID、同一 CMD_ID）
            String ackJson = objectMapper.writeValueAsString(ack);
            responseEventPublisher.publish(
                    TARGET_ASE,    // 目標系統：ase
                    ackJson,       // 完整 payload（字串）
                    MqttMessageType.ACK,
                    tid,           // 保持同一個 TID
                    "S067"         // CMD_ID
            );
            log.info("[S067][BRIDGE] SEEC → ASE（ACK）已轉發：tid={}", tid);
        } else {
            //log.debug("[S067][ACK] 非 SEEC 來源，僅記錄不轉發：from={}", from);
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
     * @return S067AckPayload.class
     */
    @Override
    protected Class<S067AckPayload> getAckType() {
        return S067AckPayload.class;
    }
}
