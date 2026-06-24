package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S072AckPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.repository.S072SessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S072AckHandler
 * ------------------------------------------------------------
 * - 負責處理 CMD_ID=S072 的 ACK 訊息（Tray 間隙檢查回覆）
 * - ASE 回應是否通過 Tray 間隙檢查（OK / NG）
 *
 * 處理流程：
 *   1) 將 ACK 原封不動記錄至 mqtt_message_log（審計/追蹤用）
 *   2) 依據 TID 回填 s072_session：
 *        - result / result_message
 *        - status = "ACK"（OK/NG 由 result 判斷，不在此處切 ERROR）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S072AckHandler extends AbstractAckHandler<S072AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** S072 會話表 repository，用於以 TID 回填 ACK 結果 */
    private final S072SessionRepository s072Repo;

    /**
     * 建構子
     *
     * @param objectMapper JSON 處理器
     * @param logService   MQTT 訊息記錄服務
     * @param s072Repo     S072 session repository（for TID 回填）
     */
    public S072AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService,
                          S072SessionRepository s072Repo) {
        super(objectMapper);
        this.logService = logService;
        this.s072Repo = s072Repo;
    }

    /**
     * 處理收到的 S072 ACK 訊息
     *
     * 步驟：
     *  1. 記錄到 mqtt_message_log
     *  2. 嘗試以 ack.tid 回填 s072_session 的 result/result_message/status
     *
     * @param system 發送方系統（例如 ASE）
     * @param topic  MQTT topic
     * @param ack    已反序列化的 S072AckPayload
     */
    @Override
    protected void process(String system, String topic, S072AckPayload ack) throws Exception {
        // --- 基礎欄位整理 ---
        final String tid   = ack.getTid();
        final String idDesc= ack.getIdDesc();

        final String station = ack.getMessage() == null ? "" : ack.getMessage().getLocation();

        final String rawResult = ack.getResult();
        final String rawMsg    = ack.getResultMessage() == null ? "" : ack.getResultMessage();

        // 只允許 OK / NG；其他一律視為 NG，並標註來源值
        String normalizedResult = (rawResult == null ? "" : rawResult.trim().toUpperCase());
        String normalizedMsg    = rawMsg;

        if (!"OK".equals(normalizedResult) && !"NG".equals(normalizedResult)) {
            String src = (rawResult == null || rawResult.isBlank()) ? "<blank>" : rawResult;
            normalizedMsg = "[RESULT_INVALID:" + src + "] " + rawMsg;
            normalizedResult = "NG";
            log.warn("[S072] ACK result 非法，強制視為 NG：tid={}, raw='{}'", tid, src);
        }

        log.info("[S072] 收到 Tray 間隙檢查 ACK：station={}, result={}, tid={}, idDesc={}, topic={}, system={}",
                station, normalizedResult, tid, idDesc, topic, system);

        // 1) 寫 mqtt_message_log
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        logService.record(
                topic,
                system,
                logService.getLocalSystem(),
                jsonPayload,
                MqttMessageType.ACK
        );

        // 2) 回填 s072_session（以 TID 對應）
        if (tid == null || tid.isBlank()) {
            log.warn("[S072] ACK 無 TID，無法回填 s072_session（station={}, result={}, msg='{}'）",
                    station, normalizedResult, normalizedMsg);
            return;
        }

        boolean updated = s072Repo.markAckByTid(tid, normalizedResult, normalizedMsg);
        if (!updated) {
            boolean exists = s072Repo.findByTid(tid).isPresent();
            if (!exists) {
                log.warn("[S072] 找不到 tid={} 的 s072_session，無法回填 ACK（station={}, result={}, msg='{}'）",
                        tid, station, normalizedResult, normalizedMsg);
            } else {
                log.warn("[S072] 回填 ACK 至 s072_session 失敗：tid={}（station={}, result={}, msg='{}'）",
                        tid, station, normalizedResult, normalizedMsg);
            }
        } else {
            log.info("[S072] 已回填 s072_session：tid={}, station={}, status=ACK, result={}, msg='{}'",
                    tid, station, normalizedResult, normalizedMsg);
        }
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S072"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S072";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S072AckPayload.class
     */
    @Override
    protected Class<S072AckPayload> getAckType() {
        return S072AckPayload.class;
    }
}
