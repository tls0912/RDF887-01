package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.a015.A015BridgeCoordinator;
import com.czkuo.rdf88701.application.mqtt.a015.A015PlcStrategyService;
import com.czkuo.rdf88701.application.mqtt.a015.A015ZipStrategyService;
import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.command.A015CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

/**
 * A015CommandHandler
 * - 負責處理 CMD_ID=A015 的指令（AGV 到達 EQP 事件）
 * - SEEC → SAA，通知 AGV 抵達機台位置，等待 SAA 執行關閉光閘
 * - 處理流程：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. 依 DEST_LOC 分流三種策略（#1 ZIP、#2 PLC、#3 其他橋接）
 *   3. [本指令不回 ACK]：實際回覆時機由各策略服務自行觸發
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class A015CommandHandler extends AbstractCommandHandler<A015CommandPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 系統識別（如 SAA/SEEC），由 context 提供 */
    private final SystemContext systemContext;

    /** 策略#1（STK01/02 → ZIP PortLockUnlock → DONE） */
    private final A015ZipStrategyService zipStrategy;

    /** 策略#2（STK03/04/05 → PLC W0015/W1015 → DONE） */
    private final A015PlcStrategyService plcStrategy;

    /** 策略#3（其他 → 橋接 SEEC↔ASE，同一個 TID） */
    private final A015BridgeCoordinator bridge;

    public A015CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              A015ZipStrategyService zipStrategy,
                              A015PlcStrategyService plcStrategy,
                              A015BridgeCoordinator bridge) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.zipStrategy = zipStrategy;
        this.plcStrategy = plcStrategy;
        this.bridge = bridge;
    }

    /**
     * 處理收到的 A015 指令
     */
    @Override
    protected void process(String system, String topic, A015CommandPayload command, MqttMessageType type) throws Exception {
        // 0) 基本文字欄位
        String agvName = command.getMessage() != null ? command.getMessage().getDeviceName() : "";
        String destLoc = command.getMessage() != null ? command.getMessage().getDestLoc() : "";
        log.info("[A015] 收到 AGV 到達 EQP 事件：TID={}, topic={}, system={}, DEVICE_NAME={}, DEST_LOC={}",
                command.getTid(), topic, system, agvName, destLoc);

        // 1) 記錄 COMMAND 至 mqtt_message_log（審計/追蹤）
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,                         // 來源 topic
                system,                        // sender（如 seec/ase）
                systemContext.getSystemCode(), // receiver（我方系統代碼）
                payload,
                MqttMessageType.COMMAND
        );

        // 2) 分流三種策略（不阻塞，不在此回 ACK）
        // 正規化 DEST_LOC（僅 STK01~STK05 會進策略）
        String dl = (destLoc == null) ? "" : destLoc.trim().toUpperCase();

        int idx = dl.indexOf("_");
        if (idx > 0) {
            dl = dl.substring(0, idx);
        }
        switch (dl) {
            // ---- 策略#1：ZIP PortLockUnlock ----
            case "STK01":
            case "STK02":
                if (Strings.isEmpty(command.getResult())) {
                    zipStrategy.handle(system /* 來源=SEEC */, command);
                }
                break;

            // ---- 策略#2：PLC interlock ----
            case "STK03":
            case "STK04":
            case "STK05":
                if (Strings.isEmpty(command.getResult())) {
                    plcStrategy.handle(system /* 來源=SEEC */, command);
                }
                break;

            // ---- 策略#3：其他 → 橋接（SEEC→ASE）----
            default:
                bridge.onCommandFromSeec(command);
                break;
        }
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     */
    @Override
    protected String getCmdIdInternal() {
        return "A015";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     */
    @Override
    protected Class<A015CommandPayload> getCommandType() {
        return A015CommandPayload.class;
    }
}
