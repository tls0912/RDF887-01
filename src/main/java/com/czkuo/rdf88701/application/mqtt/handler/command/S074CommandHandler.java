package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S074AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S074CommandPayload;
import com.czkuo.rdf88701.domain.service.mission.MissionQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * S074CommandHandler
 * - 負責處理 CMD_ID=S074 的指令（任務查詢請求）
 * - ASE 發送詢問目前未完成搬運任務清單
 * - 處理流程：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. 查詢任務資料（透過 MissionQueryService）
 *   3. 回傳 ACK（任務清單）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S074CommandHandler extends AbstractCommandHandler<S074CommandPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 系統識別（如 SAA/SEEC），由 context 提供 */
    private final SystemContext systemContext;

    /** 任務查詢服務 */
    private final MissionQueryService missionQueryService;

    /**
     * 建構子
     *
     * @param objectMapper           JSON 處理器
     * @param responseEventPublisher Spring Event 發送器
     * @param logService             MQTT 訊息記錄服務
     * @param systemContext          系統識別 context
     * @param missionQueryService    任務查詢服務
     */
    public S074CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              MissionQueryService missionQueryService) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.missionQueryService = missionQueryService;
    }

    /**
     * 處理收到的 S074 指令
     *
     * @param system  來源系統（如 ASE）
     * @param topic   MQTT topic
     * @param command 已反序列化的 S074CommandPayload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, S074CommandPayload command, MqttMessageType type) throws Exception {
        log.info("[S074] 收到任務查詢請求：TID={}, topic={}, from={}", command.getTid(), topic, system);

        // 1) 審計記錄 COMMAND 至 mqtt_message_log
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(topic, system, systemContext.getSystemCode(), payload, MqttMessageType.COMMAND);

        // 2) 查詢未完成任務列表
        List<S074AckPayload.MissionItem> items;
        String result = "OK";
        String resultMsg;

        try {
            items = missionQueryService.queryPendingMissions();
            resultMsg = "missions=" + (items != null ? items.size() : 0);
            log.info("[S074] 查詢完成：{}", resultMsg);
        } catch (Exception e) {
            log.error("[S074] 查詢任務清單失敗", e);
            items = Collections.emptyList();
            result = "NG";
            // 避免把太長的 exception 傳出去，只留訊息
            resultMsg = e.getMessage() != null ? e.getMessage() : "query failed";
        }

        // 3) 組建 ACK payload
        S074AckPayload ack = new S074AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S074");
        ack.setTid(command.getTid());
        ack.setIdDesc("MISSION_LIST");

        S074AckPayload.Message msg = new S074AckPayload.Message();
        msg.setMissionList(items);
        ack.setMessage(msg);

        ack.setResult(result);
        ack.setResultMessage(resultMsg);

        // 4) 發送 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    @Override
    protected String getCmdIdInternal() { return "S074"; }

    @Override
    protected Class<S074CommandPayload> getCommandType() { return S074CommandPayload.class; }
}
