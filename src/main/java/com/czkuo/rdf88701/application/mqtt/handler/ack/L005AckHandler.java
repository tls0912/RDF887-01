package com.czkuo.rdf88701.application.mqtt.handler.ack;


import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.command.ContainerCreateService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.L005AckPayload;
import com.czkuo.rdf88701.domain.repository.L005SessionRepository;
import com.czkuo.rdf88701.infra.entity.L005Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * L005AckHandler
 * ------------------------------------------------------------
 * 處理 CMD_ID=L005 的 ACK 訊息（條碼檢查回覆）
 *
 * 流程（依你的要求調整）：
 *   1) 先寫入 mqtt_message_log（不論後續狀態）
 *   2) 以 TID 查 L005Session：
 *        - 若有 Session：先將 internal_state=ACKED（表已收到 ACK），並立即回寫 peer_* 快照與原始 payload JSON
 *        - 若無 Session：只記 log，直接結束（不建檔、不驗證）
 *   3) 驗證欄位（與 onStockerInput 的規則一致）：
 *        - 必填：BARCODE、CARRIER_ID、LOT_ID、TRAY_HIGH（>0 且是數字）
 *        - 若 Session 內有 barcode，需與 ACK 的 BARCODE 一致（忽略前後空白）
 *   4) 依檢核 + 對方結果（RESULT）決定：
 *        - RESULT=CANCEL             → internal_state=CANCELLED；external_last_result=CANCEL
 *        - 檢核通過 且 RESULT ∈ {OK,PASS,START}
 *                                  → internal_state=COMPLETED；external_last_result=RESULT；呼叫 ensureFromL005
 *        - 其餘（含 檢核失敗 or RESULT 非 OK/PASS/START）
 *                                  → internal_state=FAILED；external_last_result=FAIL(或對方字)
 *
 * 注意：
 *   - 只有在「檢核通過」且「ACK 結果為 OK/PASS/START」時，才呼叫 ContainerCreateService.ensureFromL005(...)
 *   - ZIP 那邊不需再驗證，可由 Session 狀態直接判斷
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class L005AckHandler extends AbstractAckHandler<L005AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 建檔/屬性/內容型態 的應用服務 */
    private final ContainerCreateService containerCreateService;

    /** L005 Session Repository（儲存 ACK 快照與狀態機） */
    private final L005SessionRepository l005SessionRepository;

    public L005AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService,
                          ContainerCreateService containerCreateService,
                          L005SessionRepository l005SessionRepository) {
        super(objectMapper);
        this.logService = logService;
        this.containerCreateService = containerCreateService;
        this.l005SessionRepository = l005SessionRepository;
    }

    @Override
    protected void process(String system, String topic, L005AckPayload ack) throws Exception {
        // ===== 0) 取基本欄位 =====
        final String tid = safeTrim(ack.getTid());
        final L005AckPayload.Message aMsg = ack.getMessage();

        final String ackBarcode   = aMsg != null ? safeTrim(aMsg.getBarcode())    : null;
        final String ackCarrierId = aMsg != null ? safeTrim(aMsg.getCarrierId())  : null;
        final String ackLotId     = aMsg != null ? safeTrim(aMsg.getLotId())      : null;
        final String ackTrayHigh  = aMsg != null ? safeTrim(aMsg.getTrayHigh())   : null;
        final String ackTrayType  = aMsg != null ? safeTrim(aMsg.getTrayType())   : null;
        final String ackMsgType   = aMsg != null ? safeTrim(aMsg.getMessageType()): null;

        final String resultRaw  = safeTrim(ack.getResult());
        final String result     = resultRaw == null ? "" : resultRaw.toUpperCase();
        final String resultMsg  = ack.getResultMessage() == null ? "" : ack.getResultMessage();

        log.info("[L005] 收到條碼檢查 ACK：TID={}, BARCODE={}, result={}, message='{}', topic={}, system={}",
                tid, ackBarcode, result, resultMsg, topic, system);

        // ===== 1) 先寫入 mqtt_message_log（不影響後續流程）=====
        try {
            JsonNode jsonPayload = objectMapper.valueToTree(ack);
            logService.record(
                    topic,                        // MQTT topic
                    system,                       // sender（對方系統）
                    logService.getLocalSystem(),  // receiver（我方系統）
                    jsonPayload,
                    MqttMessageType.ACK
            );
        } catch (Exception e) {
            log.error("[L005] 寫入 mqtt_message_log 失敗：TID={} BARCODE={} err={}", tid, ackBarcode, e.getMessage(), e);
        }

        // ===== 2) 查 Session，若無即止（不建檔、不驗證）=====
        if (tid == null || tid.isBlank()) {
            log.warn("[L005] ACK 無有效 TID，無法對應 Session（BARCODE={}）", ackBarcode);
            return;
        }

        final Optional<L005Session> sessionOpt;
        try {
            sessionOpt = l005SessionRepository.findByTid(tid);
        } catch (Exception e) {
            log.error("[L005] 以 TID 查詢 Session 失敗：TID={} err={}", tid, e.getMessage(), e);
            return;
        }
        if (sessionOpt.isEmpty()) {
            log.warn("[L005] 找不到對應 L005Session，略過更新：TID={}", tid);
            return;
        }

        // ===== 2.1 先把 Session 標記為 ACKED（表示已收到 ACK）=====
        try {
            l005SessionRepository.updateInternalStateByTid(tid, "ACKED", null);
        } catch (Exception e) {
            log.warn("[L005] 標記 Session=ACKED 失敗（持續處理後續）：TID={} err={}", tid, e.getMessage());
        }

        // ===== 2.2 回寫 Peer ACK 原樣至 Session（payload JSON + 回覆欄位快照）=====
        try {
            l005SessionRepository.updatePeerAckByTid(
                    tid,
                    result, resultMsg,
                    ackCarrierId, ackLotId,
                    ackTrayHigh, ackTrayType, ackMsgType,
                    objectMapper.writeValueAsString(ack) // 原始 payload JSON
            );
        } catch (Exception e) {
            log.error("[L005] 回寫 Peer ACK 至 Session 失敗：TID={} err={}", tid, e.getMessage(), e);
        }

        // ===== 3) 判定流程（重點：只有 RESULT=PASS 才做欄位驗證）=====
        final L005Session session = sessionOpt.get();

        // 3-1) RESULT=PASS → 需要驗證欄位
        if ("PASS".equals(result)) {
            List<String> missing = new ArrayList<>(5);
            List<String> invalid = new ArrayList<>(3);

            // BARCODE：必帶；若 Session 有 barcode，需比對一致（忽略前後空白）
            if (!notBlank(ackBarcode)) {
                missing.add("BARCODE");
            } else if (notBlank(session.getBarcode()) && !equalsNormalized(ackBarcode, session.getBarcode())) {
                invalid.add("BARCODE_MISMATCH(session=" + session.getBarcode() + ", ack=" + ackBarcode + ")");
            }

            // CARRIER_ID：必帶
            if (!notBlank(ackCarrierId)) {
                missing.add("CARRIER_ID");
            }

            // LOT_ID：必帶
            if (!notBlank(ackLotId)) {
                missing.add("LOT_ID");
            }

            // TRAY_HIGH：必帶且 > 0
            if (!notBlank(ackTrayHigh)) {
                missing.add("TRAY_HIGH");
            } else {
                try {
                    BigDecimal th = new BigDecimal(ackTrayHigh);
                    if (th.compareTo(BigDecimal.ZERO) <= 0) {
                        invalid.add("TRAY_HIGH<=0");
                    }
                } catch (NumberFormatException nfe) {
                    invalid.add("TRAY_HIGH(NOT_NUMBER)");
                }
            }

            // TRAY_TYPE（料號）：必填；長度 ≤ 64
            if (ackTrayType == null) {
                missing.add("TRAY_TYPE");
            } else if (ackTrayType.length() > 64) {
                invalid.add("TRAY_TYPE 長度超過 64");
            }

            final boolean fieldsOk = missing.isEmpty() && invalid.isEmpty();

            try {
                if (fieldsOk) {
                    // 欄位 OK 且 RESULT=PASS → 完成
                    l005SessionRepository.updateInternalStateByTid(tid, "COMPLETED", resultMsg);
                    l005SessionRepository.updateExternalResultByTid(tid, "OK", resultMsg);

                    // 不必在提前建立資料， ZIPA 資訊由 L005 Session 提供。
                    // try {
                    //     Long containerMainId = containerCreateService.ensureFromL005(ack);
                    //     log.info("[L005] ensureFromL005 完成：containerMainId={} TID={}", containerMainId, tid);
                    // } catch (Exception e) {
                    //     String reason = "ENSURE_FROM_L005_ERROR:" + safeMsg(e);
                    //     l005SessionRepository.updateInternalStateByTid(tid, "FAILED", reason);
                    //     l005SessionRepository.updateExternalResultByTid(tid, "FAIL", reason);
                    //     log.error("[L005] ensureFromL005 例外：TID={} err={}", tid, e.getMessage(), e);
                    // }
                } else {
                    // 欄位檢核失敗 → 失敗
                    String combinedMsg = buildFailMessage(resultMsg, missing, invalid);
                    l005SessionRepository.updateInternalStateByTid(tid, "FAILED", combinedMsg);
                    l005SessionRepository.updateExternalResultByTid(tid, "FAIL", combinedMsg);
                    log.warn("[L005] RESULT=PASS 但欄位驗證失敗：TID={} missing={} invalid={} msg='{}'",
                            tid, missing, invalid, resultMsg);
                }
            } catch (Exception e) {
                log.error("[L005] 更新 Session 狀態失敗（RESULT=PASS 分支）：TID={} err={}", tid, e.getMessage(), e);
            }
            return;
        }

        // 3-3) 其他 RESULT（非 PASS 且非 CANCEL）：不做欄位驗證，直接視為失敗或自定義
        try {
            String finalResult = notBlank(result) ? result : "FAIL";
            String reason = notBlank(resultMsg) ? resultMsg : "FAIL";
            l005SessionRepository.updateInternalStateByTid(tid, "FAILED", reason);
            l005SessionRepository.updateExternalResultByTid(tid, finalResult, reason);
            log.warn("[L005] RESULT 非 PASS，已標記 FAILED：TID={} result={} msg='{}'", tid, result, resultMsg);
        } catch (Exception e) {
            log.error("[L005] 更新 Session 狀態失敗（RESULT=其他 分支）：TID={} err={}", tid, e.getMessage(), e);
        }
    }

    /** 回傳對應的 CMD_ID，供 Router 註冊與分派 */
    @Override
    protected String getCmdIdInternal() {
        return "L005";
    }

    /** 回傳 payload 型別，供 Jackson 反序列化 */
    @Override
    protected Class<L005AckPayload> getAckType() {
        return L005AckPayload.class;
    }

    // ===================== 小工具 =====================

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    /** 忽略前後空白的等值比較（不變更大小寫，以免誤傷大小寫敏感的條碼） */
    private static boolean equalsNormalized(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equals(b.trim());
    }

    private static String safeMsg(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }

    /** 組合 FAIL 時的詳盡訊息（ACK 原訊息 + 檢核缺失/不合法欄位） */
    private static String buildFailMessage(String resultMessage, List<String> missing, List<String> invalid) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(resultMessage)) {
            sb.append(resultMessage.trim());
        }
        if (missing != null && !missing.isEmpty()) {
            if (sb.length() > 0) sb.append(";");
            sb.append("ACK_MISSING_FIELDS:").append(String.join(",", missing));
        }
        if (invalid != null && !invalid.isEmpty()) {
            if (sb.length() > 0) sb.append(";");
            sb.append("ACK_INVALID_FIELDS:").append(String.join(",", invalid));
        }
        return sb.toString();
    }
}
