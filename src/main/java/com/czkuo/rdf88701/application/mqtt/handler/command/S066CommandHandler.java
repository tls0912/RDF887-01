package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.label.LabelingInfoService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S066AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S066CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S066CommandHandler
 * ------------------------------------------------------------
 * - 負責處理 CMD_ID=S066 的指令（標籤資訊印製 - 格式二 / 詳細版）
 * - 流程：
 *   1) 記錄 COMMAND 至 mqtt_message_log（完整保留原始 JSON）
 *   2) 將 S066 內容交由 LabelingInfoService 統一為 {type,data,norm} 並 UPSERT 至 labeling_info
 *   3) 回覆 ACK（OK/NG）
 *
 * 設計說明：
 * - 不在 Handler 內直接 new/塞 LabelingInfo，避免與解析/列印邏輯脫鉤。
 * - 由 Service 產生一致的 payload 結構，S065 / S066 皆能被 extractLabelVars() 穩定解析。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S066CommandHandler extends AbstractCommandHandler<S066CommandPayload> {

    /** 寫 mqtt_message_log */
    private final MqttMessageLogService logService;
    /** 系統識別（本系統的代號，用於 message log receiver） */
    private final SystemContext systemContext;
    /** 標籤資訊服務（負責資料統一化、UPSERT 與後續領取/解析） */
    private final LabelingInfoService labelingInfoService;

    public S066CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              LabelingInfoService labelingInfoService) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.labelingInfoService = labelingInfoService;
    }

    /**
     * 處理收到的 S066 指令
     *
     * @param system  來源系統（如 ASE）
     * @param topic   MQTT topic
     * @param command 已反序列化的 S066CommandPayload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, S066CommandPayload command, MqttMessageType type) throws Exception {
        final String tid = command.getTid();
        final int tagCount = (command.getMessage() == null) ? 0 : command.getMessage().size();

        log.info("[S066] 收到標籤資訊印製（格式二）指令：TID={}, topic={}, system={}, tagCount={}",
                tid, topic, system, tagCount);

        // 1) 記錄 COMMAND 至 mqtt_message_log（完整保留 JSON）
        JsonNode fullPayload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                               // sender：對方系統（如 ASE）
                systemContext.getSystemCode(),        // receiver：我方系統代碼
                fullPayload,
                MqttMessageType.COMMAND
        );

        // 2) 交給 Service 做統一結構 + UPSERT（payload = {type:"S066", data, norm}）
        boolean allOk = true;
        try {
            labelingInfoService.upsertFromS066(tid, command.getMessage(), fullPayload);
            log.info("[S066] upsertFromS066 完成：TID={}, count={}", tid, tagCount);
        } catch (Exception e) {
            allOk = false;
            log.error("[S066] upsertFromS066 發生例外：TID={}", tid, e);
        }

        // 3) 回覆 ACK（若任何一筆失敗則回 NG）
        S066AckPayload ack = new S066AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S066");
        ack.setTid(tid);
        ack.setIdDesc("TAG_INFO");
        ack.setResult(allOk ? "OK" : "NG");
        ack.setResultMessage(allOk ? "" : "Persist labeling_info failed");

        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    @Override
    protected String getCmdIdInternal() {
        return "S066";
    }

    @Override
    protected Class<S066CommandPayload> getCommandType() {
        return S066CommandPayload.class;
    }
}
