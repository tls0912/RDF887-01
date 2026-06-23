package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S011AckPayload;
import com.czkuo.rdf88701.domain.repository.DoorAccessInfoRepository;
import com.czkuo.rdf88701.domain.repository.HmiDisplayTaskRepository;
import com.czkuo.rdf88701.infra.entity.DoorAccessInfo;
import com.czkuo.rdf88701.infra.entity.HmiDisplayTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * S011AckHandler
 * <p>
 * 負責處理 CMD_ID=S011 的 ACK（安全門開啟請求的回覆）。
 *
 * 職責：
 *  1) 將 ACK 訊息記錄到 mqtt_message_log（留完整 payload）
 *  2) 依 TID 更新 door_access_info 中對應列：
 *     - status      : ACK_OK / ACK_NG
 *     - ack_result  : OK / NG
 *     - ack_message : RESULT_MESSAGE
 *     - staff_list  : MESSAGE.STAFF_LIST（JSON array；若無則為 null）
 *     - ack_at      : 現在時間
 *
 * 寫回 PLC：不在此處進行，交由「Writer」元件後續撈取
 * （條件：writeback_status=WAITING）再依規則回寫 W0020/… 與握手。
 */
@Slf4j
@Component
public class S011AckHandler extends AbstractAckHandler<S011AckPayload> {

    private final MqttMessageLogService logService;
    private final DoorAccessInfoRepository doorAccessInfoRepository;
    private final HmiDisplayTaskRepository hmiRepo;
    public S011AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService,
                          DoorAccessInfoRepository doorAccessInfoRepository,
                          HmiDisplayTaskRepository hmiDisplayTaskRepository) {
        super(objectMapper);
        this.logService = logService;
        this.doorAccessInfoRepository = doorAccessInfoRepository;
        hmiRepo= hmiDisplayTaskRepository;
    }

    /**
     * 主處理邏輯：
     * 1) 記錄 ACK -> mqtt_message_log
     * 2) 以 TID 查 door_access_info，若存在則更新 ACK 結果與人員清單等欄位
     *
     * @param system 發送系統（如 ASE）
     * @param topic  MQTT Topic（如 ase-to-saa）
     * @param ack    已反序列化的 ACK payload
     */
    @Override
    protected void process(String system, String topic, S011AckPayload ack) throws Exception {
        final String tid = ack.getTid();
        final String result = ack.getResult(); // "OK" 或 "NG"
        final List<String> staffList = (ack.getMessage() != null) ? ack.getMessage().getStaffList() : null;

        log.info("[S011] 收到安全門開啟回覆：TID={}, RESULT={}, STAFFS={}",
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

        /* 2) 依 TID 更新 door_access_info */
        try {
            Optional<DoorAccessInfo> opt = doorAccessInfoRepository.findByTid(tid);
            if (opt.isEmpty()) {
                // 正常情況：送出 S011 時會先 savePending() 建一筆 PENDING
                log.warn("[S011] 找不到對應的 door_access_info 紀錄（可能未入列或已清除），tid={}", tid);
                return;
            }

            DoorAccessInfo row = opt.get();

            // 決定狀態與結果欄位
            boolean ok = "OK".equalsIgnoreCase(result);
            row.setStatus(ok ? "ACK_OK" : "ACK_NG");
            row.setAckResult(ok ? "OK" : "NG");
            row.setAckMessage(ack.getResultMessage());              // 可能為空字串
            row.setAckAt(LocalDateTime.now());

            // STAFF_LIST -> JSON string（無/空清單則存 null）
            if (staffList != null && !staffList.isEmpty()) {
                row.setStaffList(objectMapper.writeValueAsString(staffList));
            } else {
                row.setStaffList(null);
            }

            // 不在此處改 writeback_status，預期送出時為 WAITING，由 Writer 去撈寫 PLC
            boolean updated = doorAccessInfoRepository.update(row);
            if (!updated) {
                log.warn("[S011] 更新 door_access_info 失敗，tid={}", tid);
            } else {
                log.info("[S011] 已更新 door_access_info：tid={}, status={}, ackResult={}",
                        tid, row.getStatus(), row.getAckResult());
            }
            String ackResult = ack.getResult() != null ? ack.getResult().trim().toUpperCase() : "";
            String ackResultMsg = ack.getResultMessage() != null ? ack.getResultMessage() : "";
            // OK/START/PASS → 0；其他 → 1
            int resCode = ("OK".equals(ackResult) || "START".equals(ackResult) || "PASS".equals(ackResult)) ? 0 : 1;
            if (resCode != 0) {
                HmiDisplayTask task = new HmiDisplayTask();
                task.setTid(ack.getTid());
                task.setMsgEn(ack.getMessage().getDeviceName() + "，" + ack.getMessage().getSafeDoorName() + "，" + ackResultMsg);
                task.setMsgCh(ack.getMessage().getDeviceName() + "，" + ack.getMessage().getSafeDoorName() + "，" + ackResultMsg);
                task.setStatus("PENDING");
                task.setAttempts(0);
                boolean saved = hmiRepo.save(task);
            }
        } catch (Exception e) {
            log.error("[S011] 更新 door_access_info 發生例外，tid={}, err={}", tid, e.getMessage(), e);
        }
    }

    /** 對應 CMD_ID */
    @Override
    protected String getCmdIdInternal() {
        return "S011";
    }

    /** 回傳 payload 型別（提供反序列化） */
    @Override
    protected Class<S011AckPayload> getAckType() {
        return S011AckPayload.class;
    }
}
