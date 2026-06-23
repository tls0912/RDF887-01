package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.label.LabelingInfoService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S065AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S065CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * S065CommandHandler
 * ------------------------------------------------------------
 * - 負責處理 CMD_ID=S065 的指令（標籤資訊印製）
 * - 與 LabelingInfoService 對齊資料格式：payload 統一為 {type,data,norm} 的字串 JSON
 * - 流程：
 *   1) 記錄 COMMAND 至 mqtt_message_log（完整保留原始 JSON）
 *   2) 交給 LabelingInfoService.upsertFromS065() 轉換 + UPSERT 至 labeling_info
 *   3) 回覆 ACK（OK/NG）
 *
 * 設計重點：
 * - 不在 Handler 內直接 new/塞 LabelingInfo，避免與下游解析邏輯（extractLabelVars）脫鉤。
 * - 由 Service 產生一致格式，S065/S066 皆能被下游穩定解析與列印。
 */
@Slf4j
@Component
public class S065CommandHandler extends AbstractCommandHandler<S065CommandPayload> {

    /** 寫 mqtt_message_log */
    private final MqttMessageLogService logService;
    /** 系統識別（本系統的代號，用於 message log receiver） */
    private final SystemContext systemContext;
    /** 標籤資訊服務（負責資料統一化、UPSERT 與後續領取/解析） */
    private final LabelingInfoService labelingInfoService;

    public S065CommandHandler(ObjectMapper objectMapper,
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
     * 實際處理流程：
     * - 1) 將完整 S065 payload 記錄進 mqtt_message_log（COMMAND）
     * - 2) 呼叫 LabelingInfoService.upsertFromS065()：
     *      * 將 S065.MESSAGE 清單逐筆轉成 {type:"S065", data:TagInfo, norm:{…}} 字串
     *      * requestKey = tid#index（同一 TID 的多筆確保唯一）
     *      * 狀態預設 READY，expiresAt 預設 +10 分鐘（Service 內定義）
     * - 3) 若過程任何一步失敗，ACK 回 NG；否則 OK
     */
    @Override
    protected void process(String system, String topic, S065CommandPayload command, MqttMessageType type) throws Exception {
        final String tid = command.getTid();
        final int tagCount = (command.getMessage() == null) ? 0 : command.getMessage().size();

        log.info("[S065] 收到標籤資訊印製指令：TID={}, topic={}, system={}, tagCount={}",
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

        // 2) 交給 Service 做統一結構 + UPSERT
        boolean allOk = true;
        try {
            labelingInfoService.upsertFromS065(tid, command.getMessage(), fullPayload);
            log.info("[S065] upsertFromS065 完成：TID={}, count={}", tid, tagCount);
        } catch (Exception e) {
            allOk = false;
            log.error("[S065] upsertFromS065 發生例外：TID={}", tid, e);
        }

        // 3) 回覆 ACK（若任何一筆失敗則回 NG）
        S065AckPayload ack = new S065AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S065");
        ack.setTid(tid);
        ack.setIdDesc("TAG_INFO");
        ack.setResult(allOk ? "OK" : "NG");
        ack.setResultMessage(allOk ? "" : "Persist labeling_info failed");

        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    @Override
    protected String getCmdIdInternal() {
        return "S065";
    }

    @Override
    protected Class<S065CommandPayload> getCommandType() {
        return S065CommandPayload.class;
    }
}
