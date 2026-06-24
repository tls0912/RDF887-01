package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S010AckPayload;
import com.czkuo.rdf88701.domain.repository.HmiDisplayTaskRepository;
import com.czkuo.rdf88701.infra.entity.HmiDisplayTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S010AckHandler
 * - 處理 CMD_ID=S010 的 ACK 訊息（人員刷卡驗證結果）
 * - 通常由 ASE 回覆驗證結果（OK / NG）至我方 SAA。
 * - 本處理器負責：
 *   1. 記錄回覆訊息至 mqtt_message_log
 *   2. [可擴充] 根據驗證結果執行授權、警示等後續流程
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S010AckHandler extends AbstractAckHandler<S010AckPayload> {

    private final MqttMessageLogService logService;
    private final HmiDisplayTaskRepository hmiRepo;

    public S010AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService,
                          HmiDisplayTaskRepository hmiDisplayTaskRepository) {
        super(objectMapper);
        this.logService = logService;
        hmiRepo= hmiDisplayTaskRepository;
    }

    /**
     * 主處理邏輯：
     * - 接收到 S010 ACK 後記錄訊息並可執行後續業務流程。
     *
     * @param system 發送端系統（如 ase）
     * @param topic  MQTT Topic（如 ase-to-saa）
     * @param ack    已反序列化的 ACK payload
     */
    @Override
    protected void process(String system, String topic, S010AckPayload ack) throws Exception {
        log.info("[S010] 收到刷卡驗證回覆，卡號={}，結果={}，來源系統={}",
                ack.getMessage().getCardNumber(),
                ack.getResult(),
                system
        );
        // 1️⃣ 將 ACK 訊息記錄至資料庫
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        logService.record(
                topic,                          // MQTT topic
                system,                         // sender（ASE）
                logService.getLocalSystem(),    // receiver（SAA）
                jsonPayload,
                MqttMessageType.ACK
        );

        // 2️⃣ [可擴充] 根據回覆結果觸發授權或封鎖等後續流程
        String ackResult = ack.getResult() != null ? ack.getResult().trim().toUpperCase() : "";
        String ackResultMsg = ack.getResultMessage() != null ? ack.getResultMessage() : "";
        // OK/START/PASS → 0；其他 → 1
        int resCode = ("OK".equals(ackResult) || "START".equals(ackResult) || "PASS".equals(ackResult)) ? 0 : 1;
        {
            HmiDisplayTask task = new HmiDisplayTask();
            task.setTid(ack.getTid());
            task.setMsgEn(ack.getMessage().getDeviceName() + "，" + ack.getMessage().getSafeDoorName() + "，" + ackResultMsg);
            task.setMsgCh(ack.getMessage().getDeviceName() + "，" + ack.getMessage().getSafeDoorName() + "，" + ackResultMsg);
            task.setStatus("PENDING");
            task.setAttempts(0);
            boolean saved = hmiRepo.save(task);
        }
    }

    /**
     * 回傳指令代碼字串（固定為 S010）
     *
     * @return "S010"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S010";
    }

    /**
     * 回傳 payload 型別，提供給 Jackson 做反序列化
     *
     * @return S010AckPayload.class
     */
    @Override
    protected Class<S010AckPayload> getAckType() {
        return S010AckPayload.class;
    }
}