package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S013AckPayload;
import com.czkuo.rdf88701.domain.repository.HmiDisplayTaskRepository;
import com.czkuo.rdf88701.domain.repository.StartAccessInfoRepository;
import com.czkuo.rdf88701.infra.entity.HmiDisplayTask;
import com.czkuo.rdf88701.infra.entity.StartAccessInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * S013AckHandler
 * <p>
 * 負責處理 CMD_ID=S013 的 ACK（RESET/START 人員驗證結果回覆）。
 * <p>
 * 職責：
 *  1) 將 ACK 訊息記錄到 mqtt_message_log（保留完整 payload）
 *  2) 依 TID 更新 start_access_info 中對應列：
 *     - status      : ACK_OK / ACK_NG
 *     - ack_result  : OK / NG
 *     - ack_message : RESULT_MESSAGE
 *     - staff_list  : MESSAGE.STAFF_LIST（JSON array；若無則為 null）
 *     - ack_at      : 現在時間
 * <p>
 * 寫回 PLC：不在此處進行，交由「Writer」元件後續撈取
 * （條件：writeback_status=WAITING）再依規則回寫 ReturnCode 與握手。
 */
@Slf4j
@Component
public class S013AckHandler extends AbstractAckHandler<S013AckPayload> {

    /**
     * 記錄 ACK 到 mqtt_message_log 的服務
     */
    private final MqttMessageLogService logService;

    /**
     * 存取 start_access_info 的 Repository（以 TID 鏈結狀態）
     */
    private final StartAccessInfoRepository startAccessInfoRepository;
    private final HmiDisplayTaskRepository hmiRepo;

    public S013AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService,
                          StartAccessInfoRepository startAccessInfoRepository,
                          HmiDisplayTaskRepository hmiDisplayTaskRepository) {
        super(objectMapper);
        this.logService = logService;
        this.startAccessInfoRepository = startAccessInfoRepository;
        hmiRepo= hmiDisplayTaskRepository;
    }

    /**
     * 主處理邏輯：
     * 1) 記錄 ACK -> mqtt_message_log
     * 2) 以 TID 查 start_access_info，若存在則更新 ACK 結果與人員清單等欄位
     *
     * @param system 發送該 ACK 的對方系統（如 ASE）
     * @param topic  MQTT topic（如 ase_to_saa）
     * @param ack    已反序列化的 S013AckPayload
     */
    @Override
    protected void process(String system, String topic, S013AckPayload ack) throws Exception {
        final String tid = ack.getTid();
        final String result = ack.getResult(); // "OK" 或 "NG"
        final List<String> staffList = (ack.getMessage() != null) ? ack.getMessage().getStaffList() : null;

        log.info("[S013] 收到 RESET/START 驗證 ACK：TID={}, RESULT={}, STAFFS={}",
                tid, result, staffList);

        /* 1) 記錄 ACK 至 mqtt_message_log（保留完整 payload） */
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        logService.record(
                topic,
                system,                       // sender：對方系統
                logService.getLocalSystem(),  // receiver：本系統
                jsonPayload,
                MqttMessageType.ACK
        );

        /* 2) 依 TID 更新 start_access_info */
        try {
            Optional<StartAccessInfo> opt = startAccessInfoRepository.findByTid(tid);
            if (opt.isEmpty()) {
                // 正常情況：送出 S013 時會先 savePending() 建一筆 PENDING
                log.warn("[S013] 找不到對應的 start_access_info 紀錄（可能未入列或已清除），tid={}", tid);
                return;
            }

            StartAccessInfo row = opt.get();

            // 轉成狀態與結果欄位（用 Repository 的常數避免魔法字串）
            boolean ok = "OK".equalsIgnoreCase(result);
            row.setStatus(ok
                    ? StartAccessInfoRepository.STATUS_ACK_OK
                    : StartAccessInfoRepository.STATUS_ACK_NG);
            row.setAckResult(ok
                    ? StartAccessInfoRepository.ACK_OK
                    : StartAccessInfoRepository.ACK_NG);
            row.setAckMessage(ack.getResultMessage());   // 可能為空字串
            row.setAckAt(LocalDateTime.now());

            // STAFF_LIST -> JSON string（無/空清單則存 null）
            if (staffList != null && !staffList.isEmpty()) {
                row.setStaffList(objectMapper.writeValueAsString(staffList));
            } else {
                row.setStaffList(null);
            }

            // 不在此處改 writeback_status，維持 WAITING，交由 Writer 去回寫 PLC
            boolean updated = startAccessInfoRepository.update(row);
            if (!updated) {
                log.warn("[S013] 更新 start_access_info 失敗，tid={}", tid);
            } else {
                log.info("[S013] 已更新 start_access_info：tid={}, status={}, ackResult={}",
                        tid, row.getStatus(), row.getAckResult());
            }
            String ackResult = ack.getResult() != null ? ack.getResult().trim().toUpperCase() : "";
            String ackResultMsg = ack.getResultMessage() != null ? ack.getResultMessage() : "";
            // OK/START/PASS → 0；其他 → 1
            int resCode = ("OK".equals(ackResult) || "START".equals(ackResult) || "PASS".equals(ackResult)) ? 0 : 1;
            if (resCode != 0) {
                HmiDisplayTask task = new HmiDisplayTask();
                task.setTid(ack.getTid());
                task.setMsgEn(ack.getMessage().getDeviceName() + "，" + ackResultMsg);
                task.setMsgCh(ack.getMessage().getDeviceName() + "，" + ackResultMsg);
                task.setStatus("PENDING");
                task.setAttempts(0);
                boolean saved = hmiRepo.save(task);
            }
        } catch (Exception e) {
            log.error("[S013] 更新 start_access_info 發生例外，tid={}, err={}", tid, e.getMessage(), e);
        }
    }

    /**
     * 供 Router 註冊與匹配：此 Handler 處理的 CMD_ID
     */
    @Override
    protected String getCmdIdInternal() {
        return "S013";
    }

    /**
     * 回傳 payload 型別（供 Jackson 反序列化）
     */
    @Override
    protected Class<S013AckPayload> getAckType() {
        return S013AckPayload.class;
    }
}
