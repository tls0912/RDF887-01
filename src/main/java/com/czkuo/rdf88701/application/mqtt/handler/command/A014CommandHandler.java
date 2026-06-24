package com.czkuo.rdf88701.application.mqtt.handler.command;


import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.command.A014CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A014CommandHandler
 * - 負責處理 CMD_ID=A014 的指令（AGV回到換電站事件）
 * - SEEC → SAA 回報 AGV 回到換電站與電池、里程資訊
 * - 處理流程：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. [預留] 處理 AGV 進站資訊（電池、里程、異常等）
 *   3. [本指令不回 ACK]
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class A014CommandHandler extends AbstractCommandHandler<A014CommandPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 系統識別（如 SAA/SEEC），由 context 提供 */
    private final SystemContext systemContext;

    public A014CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
    }

    /**
     * 處理收到的 A014 指令
     * <p>
     * 1. 記錄 AGV 回到換電站 COMMAND 至資料庫
     * 2. [預留] AGV 電池、里程同步、異常等業務
     * 3. [新增] 幫 SEEC 轉發 COMMAND 給 ASE（同一個 TID、CMD_ID=A014）
     * 4. [本指令不回 ACK]
     */
    @Override
    protected void process(String system, String topic, A014CommandPayload command, MqttMessageType type) throws Exception {
        A014CommandPayload.Message msg = command.getMessage();
        String agvName   = (msg != null) ? msg.getDeviceName() : "";
        String batteryId = (msg != null) ? msg.getBatteryId()  : "";
        log.info("[A014] 收到 AGV 回到換電站事件：TID={}, topic={}, system={}, DEVICE_NAME={}, BATTERY_ID={}, ODO={}, TRIP={}",
                command.getTid(), topic, system, agvName, batteryId,
                (msg != null ? msg.getOdo() : ""), (msg != null ? msg.getTrip() : ""));

        // 1️⃣ 記錄 COMMAND 至 mqtt_message_log
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,
                systemContext.getSystemCode(),
                payload,
                MqttMessageType.COMMAND
        );

        // 2️⃣ [預留] AGV 電池、里程同步、UI/異常業務

        // 3️⃣ [新增] 幫 SEEC 轉發 COMMAND 給 ASE（保留原始內容；同一個 TID/CMD_ID）
        try {
            // 僅在來源為 SEEC 時進行橋接，避免循環轉發
            if ("seec".equalsIgnoreCase(system)) {
                String json = objectMapper.writeValueAsString(command);
                responseEventPublisher.publish(
                        "ase",               // 目標系統：ASE
                        json,                // 完整 payload（字串）
                        MqttMessageType.COMMAND,
                        command.getTid(),    // 同一個 TID
                        "A014"               // CMD_ID
                );
                log.info("[A014] 已轉發 COMMAND 給 ASE：tid={}, device={}, batteryId={}",
                        command.getTid(), agvName, batteryId);
            } else {
                //log.debug("[A014] 來源非 SEEC（system={}），略過橋接至 ASE。", system);
            }
        } catch (Exception e) {
            log.error("[A014] 轉發 ASE 失敗：tid={}, err={}", command.getTid(), e.getMessage(), e);
        }

        // 4️⃣ [不回 ACK]
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     */
    @Override
    protected String getCmdIdInternal() {
        return "A014";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     */
    @Override
    protected Class<A014CommandPayload> getCommandType() {
        return A014CommandPayload.class;
    }
}
