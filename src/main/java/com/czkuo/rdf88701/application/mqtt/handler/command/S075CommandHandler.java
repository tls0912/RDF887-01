package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S075AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S075CommandPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.service.mission.MissionQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class S075CommandHandler extends AbstractCommandHandler<S075CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;
    private final MissionQueryService missionQueryService;

    public S075CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              MissionQueryService missionQueryService) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.missionQueryService = missionQueryService;
    }

    @Override
    protected void process(String system, String topic, S075CommandPayload command, MqttMessageType type) throws Exception {
        log.info("[S075] 收到單一任務查詢：REQ_TID={}, topic={}, from={}", command.getTid(), topic, system);

        // 1) 記錄 COMMAND
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(topic, system, systemContext.getSystemCode(), payload, MqttMessageType.COMMAND);

        // 2) 解析請求欄位（使用你 DTO 的命名）
        String qCmd = (command.getMessage() != null) ? command.getMessage().getCommand() : null; // COMMOND
        String qTid = (command.getMessage() != null) ? command.getMessage().getTid()     : null; // 目標任務的 TID

        // 3) 查詢單一任務狀態（可能回 NULL）
        MissionQueryService.SingleMissionStatus st = missionQueryService.querySingleMissionStatus(qCmd, qTid);

        // 4) 組 ACK
        S075AckPayload ack = new S075AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S075");
        ack.setTid(command.getTid());                     // 回應對應請求的 TID
        ack.setIdDesc("MISSION_STATUS_CHECK");

        S075AckPayload.Message msg = new S075AckPayload.Message();
        msg.setCommand(qCmd);                             // echo COMMOND（R007/R008/…）
        msg.setTid(qTid);                                 // echo 目標任務的 TID
        msg.setStatus(st.status());                       // "<slotId>" / "STK" / "AMR" / "NULL"
        msg.setEqpName(st.eqpName());
        msg.setEqpPort(st.eqpPort());
        ack.setMessage(msg);

        ack.setResult("OK");
        ack.setResultMessage("");

        // 5) 發 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    @Override protected String getCmdIdInternal() { return "S075"; }

    @Override protected Class<S075CommandPayload> getCommandType() { return S075CommandPayload.class; }
}
