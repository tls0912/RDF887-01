package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.command.S020CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * S020CommandHandler
 * - 負責處理 CMD_ID=S020 的指令（Event 發生通知）
 * - 廠商主動通知 ASE 發生事件（如安全門未關閉）
 * - 處理流程包含：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. [預留] 實際執行事件處理（如推播警示、DB 紀錄等）
 */
@Slf4j
@Component
public class S020CommandHandler extends AbstractCommandHandler<S020CommandPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 系統識別（如 SAA/ASE），由 context 提供 */
    private final SystemContext systemContext;

    private final MqttCommandService mqttCommandService;

    /**
     * 建構子
     *
     * @param objectMapper           JSON 處理器
     * @param responseEventPublisher Spring Event 發送器（如無 ACK 可不使用）
     * @param logService             MQTT 訊息記錄服務
     * @param systemContext          系統識別 context
     */
    public S020CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              MqttCommandService mqttCommandService) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.mqttCommandService = mqttCommandService;
    }

    /**
     * 處理收到的 S020 指令
     * <p>
     * 1. 記錄事件通知 COMMAND 訊息
     * 2. [預留] 進行事件警示、推播等業務
     * 3. [如有設計 ACK] 回覆 ACK
     *
     * @param system  來源系統（如廠商系統）
     * @param topic   MQTT topic
     * @param command 已反序列化的 S020CommandPayload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, S020CommandPayload command, MqttMessageType type) throws Exception {
        // 1️⃣ 日誌顯示收到的事件資訊
        log.info("[S020] 收到 Event 通知指令：TID={}, topic={}, system={}, ceid={}, ceidDescEn={}, ceidDescCh={}",
                command.getTid(),
                topic,
                system,
                command.getMessage() != null ? command.getMessage().getCeid() : "",
                command.getMessage() != null ? command.getMessage().getCeidDescEn() : "",
                command.getMessage() != null ? command.getMessage().getCeidDescCh() : ""
        );

        // 2️⃣ 記錄 COMMAND 訊息至 mqtt_message_log
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                                 // sender：對方系統
                systemContext.getSystemCode(),          // receiver：本系統
                payload,
                MqttMessageType.COMMAND
        );

        // 若 CEID=2003，自動發 S065（向相同對端 system 發出）
        if (command.getMessage() != null && "2003".equals(command.getMessage().getCeid())) {
            String lotId   = command.getMessage().getLotId();
            String carrierId = command.getMessage().getCarrierId();

            // if (StringUtils.hasText(lotId) && StringUtils.hasText(carrierId)) {
            if (StringUtils.hasText(carrierId)) {
                log.info("[S020→S065] 偵測到 CEID=2003，準備發送 S065：lotId={}, carrierId={}, targetSystem={}",
                        lotId, carrierId, system);

                var result = mqttCommandService.sendS065(system, lotId, carrierId);
                if (!result.isSuccess()) {
                    log.warn("[S020→S065] S065 發送失敗：{}", result.getMessage());
                }
            } else {
                log.warn("[S020→S065] CEID=2003 但 LOT_ID/CARRIERID 缺失，略過發送 S065。lotId='{}', carrierId='{}'",
                        lotId, carrierId);
            }
        }

        // 3️⃣ [預留] 進行事件通知、警示推播等業務流程
        // TODO: 依需求做事件警示、DB 入庫等處理
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S020"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S020";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S020CommandPayload.class
     */
    @Override
    protected Class<S020CommandPayload> getCommandType() {
        return S020CommandPayload.class;
    }
}
