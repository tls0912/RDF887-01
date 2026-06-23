package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S068AckPayload;
import com.czkuo.rdf88701.domain.repository.StrappingPrecheckResultRepository;
import com.czkuo.rdf88701.infra.entity.StrappingPrecheckResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * S068AckHandler
 * - 負責處理 CMD_ID=S068 的 ACK 訊息（打帶前狀態確認回覆）
 * - ASE 回覆是否允許進行打帶作業
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. 將結果落地至 strapping_precheck_result
 */
@Slf4j
@Component
public class S068AckHandler extends AbstractAckHandler<S068AckPayload> {

    private final MqttMessageLogService logService;
    private final StrappingPrecheckResultRepository precheckResultRepository;

    public S068AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService,
                          StrappingPrecheckResultRepository precheckResultRepository) {
        super(objectMapper);
        this.logService = logService;
        this.precheckResultRepository = precheckResultRepository;
    }

    @Override
    protected void process(String system, String topic, S068AckPayload ack) throws Exception {
        log.info("[S068] 收到打帶前狀態確認 ACK：tid={}, result={}, msg={}, topic={}, system={}",
                ack.getTid(), ack.getResult(), ack.getResultMessage(), topic, system);

        // 1️⃣ 記錄 ACK 至 mqtt_message_log
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        logService.record(
                topic,
                system,                       // sender
                logService.getLocalSystem(),  // receiver
                jsonPayload,
                MqttMessageType.ACK
        );

        // 2️⃣ 落地至 strapping_precheck_result
        StrappingPrecheckResult entity = new StrappingPrecheckResult();
        entity.setTid(ack.getTid());
        entity.setResult(ack.getResult());               // "OK" or "NG"
        entity.setResultMessage(ack.getResultMessage()); // 說明
        entity.setCreatedTime(LocalDateTime.now());

        boolean saved = precheckResultRepository.saveOrUpdateByTid(entity);
        if (saved) {
            log.info("[S068] 已更新打帶前狀態結果：tid={}, result={}", ack.getTid(), ack.getResult());
        } else {
            log.warn("[S068] ⚠️ 無法寫入打帶前狀態結果：tid={}", ack.getTid());
        }

        // 3️⃣ [可擴充] 依 result 做任務流轉，例如 NG 時通知 UI 或阻斷任務
    }

    @Override
    protected String getCmdIdInternal() {
        return "S068";
    }

    @Override
    protected Class<S068AckPayload> getAckType() {
        return S068AckPayload.class;
    }
}
