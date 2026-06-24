package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.a008.A008PlcStrategyService;
import com.czkuo.rdf88701.application.mqtt.a008.A008ZipStrategyService;
import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.command.A008CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * A008CommandHandler
 * - 負責處理 CMD_ID=A008 的指令（AGV 車事件）
 * - 由 SEEC 回報 AGV 當前狀態、任務執行、位置與電量等資訊
 * - 處理流程：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. [預留] 業務處理 AGV 狀態、任務更新
 *   3. [本指令一般不回 ACK]
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class A008CommandHandler extends AbstractCommandHandler<A008CommandPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 系統識別（如 SAA/SEEC），由 context 提供 */
    private final SystemContext systemContext;

    /** 策略#1：ZIP 解鎖（STK01/02） */
    private final A008ZipStrategyService zipStrategy;

    /** 策略#2：PLC 解鎖（STK03/04/05） */
    private final A008PlcStrategyService plcStrategy;

    /** 空 tray carrierId 樣式：[A-Z0-9]+ + '_' + [A-Z0-9]+（不分大小寫） */
    private static final Pattern DESTLOC_PATTERN = Pattern.compile("^[A-Za-z0-9]+_[A-Za-z0-9]+$");

    public A008CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              A008ZipStrategyService zipStrategy,
                              A008PlcStrategyService plcStrategy) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.zipStrategy = zipStrategy;
        this.plcStrategy = plcStrategy;
    }

    /**
     * 處理收到的 A008 指令
     * <p>
     * 1. 記錄 AGV 狀態與任務事件 COMMAND 至資料庫
     * 2. 只有 STK01~STK05 會觸發相對應的解鎖策略（ZIP/PLC）
     * 3. 【不論是否 STK】一律轉發 A008 到 ASE（同一個 TID、CMD_ID=A008）
     * 4. [本指令不回 ACK]
     */
    @Override
    protected void process(String system, String topic, A008CommandPayload command, MqttMessageType type) throws Exception {
        final String tid = command.getTid();
        var msg = command.getMessage();
        String device   = (msg != null ? msg.getDeviceName() : null);
        String status   = (msg != null ? msg.getStatus()     : null);
        String job      = (msg != null ? msg.getJobStatus()  : null);
        String destLoc  = (msg != null ? msg.getDestLoc()    : null);

        log.info("[A008] 收到 AGV 事件：system={}, topic={}, TID={}, DEVICE_NAME={}, STATUS={}, JOB_STATUS={}, DEST_LOC={}",
                system, topic, tid, device, status, job, destLoc);

        // 1) 記錄 COMMAND 至 mqtt_message_log
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,                         // 來源 topic
                system,                        // sender（如 seec/ase）
                systemContext.getSystemCode(), // receiver（我方系統代碼）
                payload,
                MqttMessageType.COMMAND
        );

        // 正規化 DEST_LOC（僅 STK01~STK05 會進策略）
        String dl = (destLoc == null) ? "" : destLoc.trim().toUpperCase();

        int idx = dl.indexOf("_");
        if (idx > 0) {
            dl = dl.substring(0, idx);
        }

        // 2) 依規則觸發解鎖（只對 STKxx；不阻塞、不回 ACK）
        try {
            switch (dl) {
                // ---- 策略#1：ZIP PortLockUnlock ----
                case "STK01":
                case "STK02":
                    // STK01/02 : INPUT_END → ZIP 解鎖
                    zipStrategy.handle(command);
                    break;

                // ---- 策略#2：PLC interlock ----
                case "STK03":
                case "STK04":
                case "STK05":
                    // STK03/04/05 : OUTPUT_END → PLC 解鎖
                    plcStrategy.handle(command);
                    break;

                // ---- 非 STKxx：不執行解鎖策略 ----
                default:
                    // 僅略過策略；轉發會在步驟 3 統一處理
                    break;
            }
        } catch (Exception ex) {
            log.warn("[A008] 解鎖策略執行例外：tid={}, err={}", tid, ex.getMessage(), ex);
        }

        // 3) 【一律】轉發到 ASE（保留原始內容、同一個 TID、CMD_ID=A008；不回 ACK）
        try {
            String json = objectMapper.writeValueAsString(command);
            responseEventPublisher.publish(
                    "ase",               // 目標系統：ase
                    json,                // 完整 payload（字串）
                    MqttMessageType.COMMAND,
                    tid,                 // 保持同一個 TID
                    "A008"               // CMD_ID
            );
            log.info("[A008] 已轉發給 ASE：outSystem=ase, TID={}", tid);
        } catch (Exception e) {
            log.error("[A008] 轉發 ASE 失敗：tid={}, err={}", tid, e.getMessage(), e);
        }
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     */
    @Override
    protected String getCmdIdInternal() {
        return "A008";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     */
    @Override
    protected Class<A008CommandPayload> getCommandType() {
        return A008CommandPayload.class;
    }

    // ====== 私有輔助 ======

    /** 從 DESTLOC 取出 STK_PORT */
    private static String extractStkPortFromDestLoc(String destLoc) {
        if (destLoc == null || destLoc.isBlank()) return null;
        var m = DESTLOC_PATTERN.matcher(destLoc.trim());
        return m.matches() ? m.group(1) : null;
    }
}
