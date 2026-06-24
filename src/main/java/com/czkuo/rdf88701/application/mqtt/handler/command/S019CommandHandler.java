package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S019AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S019CommandPayload;
import com.czkuo.rdf88701.domain.repository.HmiDisplayTaskRepository;
import com.czkuo.rdf88701.infra.entity.HmiDisplayTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * S019CommandHandler
 * - 負責處理 CMD_ID=S019 的指令（Robot HMI 顯示來自 MCS 的訊息）
 * - 流程：
 *   1) 記錄 COMMAND 至 mqtt_message_log
 *   2) 將訊息入列到 plc_hmi_display_task（英/中皆存，之後只拿英文字串寫 PLC）
 *   3) 回 ACK：入列成功回 OK；TID 缺失或 DB 寫入失敗回 FAIL
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S019CommandHandler extends AbstractCommandHandler<S019CommandPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 系統識別（如 SAA/SEEC），由 context 提供 */
    private final SystemContext systemContext;

    /** HMI 顯示任務儲存庫（對應表：plc_hmi_display_task） */
    private final HmiDisplayTaskRepository hmiDisplayTaskRepository;

    public S019CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              HmiDisplayTaskRepository hmiDisplayTaskRepository) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.hmiDisplayTaskRepository = hmiDisplayTaskRepository;
    }

    /**
     * 1) 記 log
     * 2) 將 S019 訊息入列 DB（以 TID 冪等）
     * 3) 回 ACK
     */
    @Override
    protected void process(String system, String topic, S019CommandPayload command, MqttMessageType type) throws Exception {
        final String tid   = command.getTid();
        final String msgEn = command.getMessage() != null ? nvl(command.getMessage().getMsgEn()) : "";
        final String msgCh = command.getMessage() != null ? nvl(command.getMessage().getMsgCh()) : "";

        log.info("[S019] 收到 Robot HMI 訊息指令：TID={}, topic={}, system={}, msgEn='{}', msgCh='{}'",
                tid, topic, system, msgEn, msgCh);

        // 1) 記錄 COMMAND 至 mqtt_message_log
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                          // sender：對方系統
                systemContext.getSystemCode(),   // receiver：本系統
                payload,
                MqttMessageType.COMMAND
        );

        // 2) 入列 DB（以 TID 冪等）
        boolean ok = true;
        String resultMessage = "queued";

        if (isBlank(tid)) {
            ok = false;
            resultMessage = "TID is required";
        } else {
            try {
                // 冪等：若已存在同 TID 則視為成功（不重複入列）
                Optional<HmiDisplayTask> existed = hmiDisplayTaskRepository.findByTid(tid);
                if (existed.isPresent()) {
                    log.info("[S019] TID={} 已存在，略過入列（冪等）", tid);
                    resultMessage = "already queued";
                } else {
                    HmiDisplayTask task = new HmiDisplayTask();
                    task.setTid(tid);
                    task.setMsgEn(msgEn);          // ⚠️ 之後寫 PLC 只用英文
                    task.setMsgCh(msgCh);          // UI/追蹤可用
                    task.setStatus("PENDING");     // 初始狀態：待寫
                    task.setAttempts(0);           // 初始重試次數

                    boolean saved = hmiDisplayTaskRepository.save(task);
                    if (!saved) {
                        ok = false;
                        resultMessage = "insert failed";
                    }
                }
            }
            // ⚠️ 子類要先 catch，否則會被父類攔截，導致「multi-catch 不相斥」或無法命中子類邏輯
            catch (DuplicateKeyException ex) {
                // unique key（TID）衝突：視為已存在，冪等成功
                log.warn("[S019] 入列唯一鍵衝突（視為冪等成功），TID={}, err={}", tid, ex.getMessage());
                resultMessage = "already queued";
            }
            catch (DataIntegrityViolationException ex) {
                // 其他資料完整性錯誤（非 DuplicateKey）
                ok = false;
                resultMessage = "db constraint error: " + ex.getMostSpecificCause().getMessage();
                log.error("[S019] 入列 DB 資料完整性錯誤，TID={}, err={}", tid, ex.getMessage(), ex);
            }
            catch (Exception ex) {
                ok = false;
                resultMessage = "db error: " + ex.getMessage();
                log.error("[S019] 入列 DB 失敗，TID={}, err={}", tid, ex.getMessage(), ex);
            }
        }

        // 3) 組 ACK
        S019AckPayload ack = new S019AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S019");
        ack.setTid(tid);
        ack.setIdDesc("TERMINAL_Display");
        ack.setResult(ok ? "OK" : "FAIL");
        ack.setResultMessage(resultMessage);

        S019AckPayload.Message ackMsg = new S019AckPayload.Message();
        ackMsg.setMsgEn(msgEn);
        ackMsg.setMsgCh(msgCh);
        ack.setMessage(ackMsg);

        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    @Override
    protected String getCmdIdInternal() {
        return "S019";
    }

    @Override
    protected Class<S019CommandPayload> getCommandType() {
        return S019CommandPayload.class;
    }

    // -----------------
    // 小工具
    // -----------------

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
