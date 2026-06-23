package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.a015.A015BridgeCoordinator;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.A015AckPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A015AckHandler
 * ------------------------------------------------------------
 * 責任：
 *   - 處理 CMD_ID=A015 的 ACK 訊息（AGV 到達 EQP 的回覆）
 *   - 來源可能包含：
 *       1) SEEC：策略#1/#2 完成後 SEEC 回 OK
 *       2) ASE ：策略#3 橋接情境下，ASE 先回 DONE
 *
 * 邏輯：
 *   1) 將 ACK 訊息落庫（mqtt_message_log）
 *   2) 通知橋接協調器（若該 TID 有橋接 session，做 ACK 轉傳）
 *   3) 其他 UI/事件推播可於此擴充
 *
 * 注意：
 *   - 不主動回 ACK
 *   - 是否橋接由 A015BridgeCoordinator 依 session 判斷
 */
@Slf4j
@Component
public class A015AckHandler extends AbstractAckHandler<A015AckPayload> {

    private final MqttMessageLogService logService;
    private final A015BridgeCoordinator bridge;
    private final ObjectMapper objectMapper;

    public A015AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService,
                          A015BridgeCoordinator bridge) {
        super(objectMapper);
        this.logService = logService;
        this.bridge = bridge;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void process(String system, String topic, A015AckPayload ack) throws Exception {
        final String tid = ack.getTid();
        final String result = ack.getResult();
        final String resultMsg = ack.getResultMessage();

        log.info("[A015] 收到 ACK：TID={}, RESULT={}, RESULT_MESSAGE='{}', topic={}, system={}",
                tid, result, resultMsg, topic, system);

        // 1) 審計：寫入 mqtt_message_log
        JsonNode json = objectMapper.valueToTree(ack);
        logService.record(topic, system, logService.getLocalSystem(), json, MqttMessageType.ACK);

        // 2) 橋接情境下的 ACK 轉傳（僅在有 session 時才會動作）
        String sys = system == null ? "" : system.trim().toLowerCase();
        if ("ase".equals(sys)) {
            bridge.onAckFromAse(ack);
        } else if ("seec".equals(sys)) {
            bridge.onAckFromSeec(ack);
        }
        // 3) 其他：UI 推播 / domain event（可擴充）
    }

    /** 回傳對應的 CMD_ID，供 Router 註冊與分派 */
    @Override
    protected String getCmdIdInternal() {
        return "A015";
    }

    /** 回傳 payload 型別，供 Jackson 反序列化 */
    @Override
    protected Class<A015AckPayload> getAckType() {
        return A015AckPayload.class;
    }
}
