package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S073AckPayload;
import com.czkuo.rdf88701.domain.repository.OcrVerificationRepository;
import com.czkuo.rdf88701.infra.entity.OcrVerification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * S073AckHandler
 * ----------------------------------------------------------------------------
 * 負責處理 CMD_ID=S073 的 ACK 訊息（Tray 拆併前資訊確認回覆）
 *
 * 目的：
 *   - ASE 回覆 PASS/FAIL/ERROR（或 OK/NG）
 *   - 回寫到 ocr_verification，讓 generator/決策流程可以往下走
 *
 * 處理流程：
 *   1) 記錄 ACK 訊息至 mqtt_message_log
 *   2) 依 tid 找對應的 ocr_verification（findByS073Tid）
 *   3) 回寫 s073_status / s073_result_code / updated_time
 *   4) 清除 retry 排程欄位（s073_next_retry_time = null），避免還在等 retry
 *
 * 注意：
 *   - 若做「重送會覆蓋 s073_tid」，舊 tid 的晚到 ACK 會找不到資料（正常現象）。
 *     若要吃晚到 ACK，需額外保存 tid history 或建立 tid->ocr_verification_id mapping。
 */
@Slf4j
@Component
public class S073AckHandler extends AbstractAckHandler<S073AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** OCR 驗證紀錄 repository，用於更新 S073 結果 */
    private final OcrVerificationRepository ocrVerificationRepository;

    /**
     * 建構子
     *
     * @param objectMapper Jackson ObjectMapper（父類用於反序列化）
     * @param logService   MQTT 訊息記錄服務
     * @param ocrVerificationRepository OCR 驗證紀錄資料存取
     */
    public S073AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService,
                          OcrVerificationRepository ocrVerificationRepository) {
        super(objectMapper);
        this.logService = logService;
        this.ocrVerificationRepository = ocrVerificationRepository;
    }

    /**
     * 處理收到的 S073 ACK 訊息
     *
     * @param system 發送方系統（對方）
     * @param topic  MQTT topic
     * @param ack    已反序列化的 S073AckPayload
     */
    @Override
    protected void process(String system, String topic, S073AckPayload ack) throws Exception {
        log.info("[S073] 收到 ACK: result={}, tid={}, topic={}, system={}",
                ack.getResult(), ack.getTid(), topic, system);

        // ---------------------------------------------------------------------
        // 1) 記錄 ACK 至 mqtt_message_log（保留原始 payload）
        // ---------------------------------------------------------------------
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        logService.record(
                topic,                        // MQTT topic
                system,                       // sender（對方系統）
                logService.getLocalSystem(),  // receiver（我方系統）
                jsonPayload,
                MqttMessageType.ACK
        );

        // ---------------------------------------------------------------------
        // 2) 找 ocr_verification（以 tid）
        // ---------------------------------------------------------------------
        String tid = ack.getTid();
        if (tid == null || tid.isBlank()) {
            log.warn("[S073] ACK 無 tid，無法回寫 ocr_verification");
            return;
        }

        Optional<OcrVerification> opt = ocrVerificationRepository.findByS073Tid(tid);
        if (opt.isEmpty()) {
            // 常見原因：做了 retry 並覆蓋 s073_tid，導致舊 tid 的晚到 ACK 找不到
            log.warn("[S073] 找不到對應 ocr_verification：tid={}（可能已重送覆蓋 tid 或資料被清除）", tid);
            return;
        }

        OcrVerification v = opt.get();

        // ---------------------------------------------------------------------
        // 3) 將 ACK result 映射成內部 s073_status
        //    - 可以依實際協議調整（例如 ACK 有 code/errCode）
        // ---------------------------------------------------------------------
        String result = ack.getResult();
        String status;
        if ("OK".equalsIgnoreCase(result) || "PASS".equalsIgnoreCase(result)) {
            status = "PASS";
        } else if ("NG".equalsIgnoreCase(result) || "FAIL".equalsIgnoreCase(result)) {
            status = "FAIL";
        } else {
            status = "ERROR";
        }

        // 結果碼：若協議有更細欄位，請改用 ack.getXXX()
        String resultCode = null;
        try {
            resultCode = result; // 至少寫回原始 result 做追查
        } catch (Throwable ignore) {
            // ignore
        }

        // ---------------------------------------------------------------------
        // 4) 回寫 DB
        //    - 回寫 s073_status / s073_result_code / updated_time
        //    - 並清掉 retry 設定，避免畫面/流程仍顯示下一次 retry
        // ---------------------------------------------------------------------
        v.setS073Status(status);
        v.setS073ResultCode(resultCode);

        // 收到 ACK 後，不需要再 retry：把 next_retry_time 清掉
        v.setS073NextRetryTime(null);

        // 其他 retry 追查欄位要不要清，看需求：
        // - retry_count / last_retry_time / sent_time 通常保留，方便事後追
        // v.setS073RetryCount(v.getS073RetryCount()); // 保留原值
        // v.setS073LastRetryTime(v.getS073LastRetryTime()); // 保留原值
        // v.setS073SentTime(v.getS073SentTime()); // 保留原值

        v.setUpdatedTime(LocalDateTime.now());

        boolean ok = ocrVerificationRepository.update(v);
        if (ok) {
            log.info("[S073] 回寫成功: id={}, cmId={}, tid={}, s073Status={}, s073ResultCode={}, retryCount={}",
                    v.getId(), v.getContainerMainId(), tid, v.getS073Status(), v.getS073ResultCode(),
                    v.getS073RetryCount());
        } else {
            log.warn("[S073] 回寫失敗: id={}, tid={}", v.getId(), tid);
        }

        // ---------------------------------------------------------------------
        // 5) 不直接觸發動作
        //    - generator（TR3）下一輪 tick 會依：
        //        local_pass + s073_status + manual_decision
        //      決定是否 DROP / WAIT / BLOCK
        // ---------------------------------------------------------------------
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S073"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S073";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S073AckPayload.class
     */
    @Override
    protected Class<S073AckPayload> getAckType() {
        return S073AckPayload.class;
    }
}
