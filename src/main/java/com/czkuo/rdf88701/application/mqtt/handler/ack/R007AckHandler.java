package com.czkuo.rdf88701.application.mqtt.handler.ack;


import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R007AckPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.repository.RobotR007TaskRepository;
import com.czkuo.rdf88701.infra.entity.RobotR007Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * R007AckHandler
 * - 負責處理 CMD_ID=R007 的 ACK 訊息（WIP(STK) 搬貨任務回覆）
 * - 支援 SEEC→SAA 及 SAA→ASE 兩種回報情境
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. 以 AMR TID 反查任務並更新 robot_r007_task 的 AMR 相關欄位
 *   3. 將 ACK.Message 中的 MISSION_TRIP / ODO / AMR_SPEED / AMR_ROBOT_SPEED 回填到任務
 *   4. 對於 END / FAIL / CANCEL，將結果「轉傳」回原系統（ASE），tid 使用任務原始 TID
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class R007AckHandler extends AbstractAckHandler<R007AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** R007 任務儲存庫（需實作 findByAmrTid / amrMarkAckStartByTid / amrMarkAckFinalByTid 等） */
    private final RobotR007TaskRepository taskRepo;

    /** 用於將 ACK 再發送到外部（例如 ASE） */
    private final MqttMessageEventPublisher publisher;

    /** 轉傳目的系統（可從設定覆蓋），預設 "ase" */
    @Value("${app.external.ase-system:ase}")
    private String aseSystem;

    public R007AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService,
                          RobotR007TaskRepository taskRepo,
                          MqttMessageEventPublisher publisher) {
        super(objectMapper);
        this.logService = logService;
        this.taskRepo = taskRepo;
        this.publisher = publisher;
    }

    /**
     * 處理收到的 R007 ACK 訊息
     *
     * 流程：
     *  1) 記錄 ACK 到 mqtt_message_log
     *  2) 以 AMR TID 反查對應任務；若查無，記 log 並結束（避免誤更新）
     *  3) 先回填 ACK.Message 的四個「遙測」欄位（MISSION_TRIP / ODO / AMR_SPEED / AMR_ROBOT_SPEED）
     *  4) 依結果分流：
     *     - OK    ：AMR 已收到任務（準同步確認）→ 僅更新最後 ACK 訊息/時間/原文
     *     - START ：AMR 開始執行 → amr_state=ACK_START、amr_ack_start_log_id、amr_last_ack_json
     *     - END   ：任務完成 → 統一走 handleFinal()，並轉傳給 ASE（result=END）
     *     - FAIL  ：任務失敗 → 統一走 handleFinal()，並轉傳給 ASE（result=FAIL）
     *     - CANCEL：任務取消 → 統一走 handleFinal()，並轉傳給 ASE（result=CANCEL）
     *
     * @param system 發送方系統
     * @param topic  MQTT topic
     * @param ack    已反序列化的 R007AckPayload
     */
    @Override
    protected void process(String system, String topic, R007AckPayload ack) throws Exception {
        final String amrTid    = safeText(ack.getTid());      // 這是 AMR 的 TID（可能為重送 TID）
        final String resultRaw = safeText(ack.getResult());
        final String result    = normalizeResult(resultRaw);  // 正規化結果：例如 "CANCLE" → "CANCEL"
        final String resultMsg = safeText(ack.getResultMessage());

        // 1) 記錄 ACK 並取得 logId（後續 START/END/FAIL/CANCEL 要掛回任務）
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        Long ackLogId = logService.recordReturningId(
                topic,
                system,                         // sender: 對方
                logService.getLocalSystem(),    // receiver: 我方
                jsonPayload,
                MqttMessageType.ACK
        );

        // 2) 以 AMR TID 反查任務
        Optional<RobotR007Task> opt = taskRepo.findByAmrTid(amrTid);
        if (opt.isEmpty()) {
            // 查無對應任務：不丟例外，以免中斷整體 ACK 處理，僅記錄錯誤供稽核
            log.error("[R007][ACK] 找不到對應任務：amrTid={}, result={}, topic={}, system={}",
                    amrTid, result, topic, system);
            return;
        }
        RobotR007Task task = opt.get();

        // 3) 先回填 ACK.MESSAGE 的 MISSION_TRIP / ODO / AMR_SPEED / AMR_ROBOT_SPEED
        //    - 僅在各欄位「有值」時才 patch，以避免覆蓋 DB 既有值
        //    - 這步不影響狀態機，純資訊補寫
        try {
            updateTaskTelemetryFromAck(task.getLogId(), ack.getMessage());
        } catch (Exception teleEx) {
            log.warn("[R007][ACK] 回填遙測資訊失敗：logId={}, err={}", task.getLogId(), teleEx.getMessage(), teleEx);
        }

        // 4) 依結果更新 task；必要時轉傳給 ASE
        switch (result) {
            case "OK" -> {
                // 4-1) OK：僅更新最後 ACK 資訊（不改 AMR 狀態機）
                handleOk(amrTid, task, ackLogId, ack);
            }
            case "START" -> {
                // 4-2) START：標記 amr_state=ACK_START、寫入 ack start log id / last ack json
                handleStart(amrTid, ackLogId, ack);
            }
            case "END" -> {
                // 4-3) END：任務完成，標記最終狀態並轉傳給 ASE
                handleFinal(amrTid, "END", "END", ackLogId, ack, null, null);
                forwardFinalToAse(task, "END", resultMsg); // 對 ASE 使用任務原始 tid
            }
            case "FAIL" -> {
                // 4-4) FAIL：任務失敗，記錄原因並轉傳給 ASE
                String failReason = firstNonBlank(resultMsg, "AMR 回覆 FAIL");
                handleFinal(amrTid, "FAIL", "FAIL", ackLogId, ack, failReason, null);
                forwardFinalToAse(task, "FAIL", failReason);
            }
            case "CANCEL" -> {
                // 4-5) CANCEL：任務取消，記錄原因並轉傳給 ASE
                String cancelReason = firstNonBlank(resultMsg, "AMR 取消任務");
                handleFinal(amrTid, "CANCEL", "CANCEL", ackLogId, ack, null, cancelReason);
                forwardFinalToAse(task, "CANCEL", cancelReason);
            }
            default -> {
                // 未知結果值：僅告警，不中斷
                log.warn("[R007][ACK] 未知結果：amrTid={}, resultRaw={}", amrTid, resultRaw);
            }
        }

        log.info("[R007][ACK] 處理完成：amrTid={}, result={}", amrTid, result);
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "R007"
     */
    @Override
    protected String getCmdIdInternal() {
        return "R007";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return R007AckPayload.class
     */
    @Override
    protected Class<R007AckPayload> getAckType() {
        return R007AckPayload.class;
    }

    // ======================= handlers =======================

    /**
     * OK：AMR 已收到任務（同步告知）
     * - 保持 amr_state=SENT，不變更最終狀態
     * - 但需記錄最後 ACK 時間/訊息/原文（amr_last_ack_time / amr_result_message / amr_last_ack_json）
     */
    private void handleOk(String amrTid, RobotR007Task t, Long ackLogId, R007AckPayload ack) {
        try {
            boolean ok = taskRepo.amrMarkAckOkByTid(amrTid, ackLogId, safeToJson(ack));
            if (!ok) {
                log.warn("[R007][ACK] 標記 OK 失敗：amrTid={}", amrTid);
            }
        } catch (Exception e) {
            log.warn("[R007][ACK] 更新 OK 訊息失敗：logId={}, err={}", t.getLogId(), e.getMessage(), e);
        }
    }

    /**
     * START：開始執行
     * - 透過 repo 專用方法（以 amrTid）寫入：
     *   amr_state=ACK_START、amr_ack_start_log_id=ackLogId、amr_last_ack_time、amr_last_ack_json
     */
    private void handleStart(String amrTid, Long ackLogId, R007AckPayload ack) {
        try {
            boolean ok = taskRepo.amrMarkAckStartByTid(amrTid, ackLogId, safeToJson(ack));
            if (!ok) {
                log.warn("[R007][ACK] 標記 START 失敗：amrTid={}", amrTid);
            }
        } catch (Exception e) {
            log.warn("[R007][ACK] 標記 START 例外：amrTid={}, err={}", amrTid, e.getMessage(), e);
        }
    }

    /**
     * END / FAIL / CANCEL：最終狀態
     * - 透過 repo 專用方法（以 amrTid）同時更新：
     *   amr_state（ACK_END / FAILED / CANCELLED）、
     *   external_last_result（END / FAIL / CANCEL）、
     *   amr_ack_end_log_id、amr_last_ack_json、amr_last_ack_time、
     *   fail_reason / cancel_reason（依情境）
     */
    private void handleFinal(String amrTid,
                             String finalState,         // ACK_END / FAILED / CANCELLED
                             String externalResult,     // END / FAIL / CANCEL
                             Long ackLogId,
                             R007AckPayload ack,
                             String failReason,
                             String cancelReason) {
        try {
            boolean ok = taskRepo.amrMarkAckFinalByTid(
                    amrTid, finalState, externalResult,
                    ackLogId, safeToJson(ack), failReason, cancelReason
            );
            if (!ok) {
                log.warn("[R007][ACK] 標記最終狀態失敗：amrTid={}, finalState={}", amrTid, finalState);
            }
        } catch (Exception e) {
            log.warn("[R007][ACK] 標記最終狀態例外：amrTid={}, finalState={}, err={}",
                    amrTid, finalState, e.getMessage(), e);
        }
    }

    /**
     * 將 END / FAIL / CANCEL 轉傳給 ASE
     * - 對 ASE 使用「任務原始 TID」（task.tid），而不是 AMR 的重送 TID
     * - MESSAGE 欄位採用任務主要資訊（LOT/CARRIER/WIP/DEST/EQP/DEVICE）
     * - resultMessage 傳遞 AMR 的原因描述
     */
    private void forwardFinalToAse(RobotR007Task task, String externalResult, String resultMessage) {
        try {
            // 組 ACK 給 ASE
            R007AckPayload out = new R007AckPayload();
            out.setCmd("ROBOT");
            out.setCmdId("R007");
            out.setTid(task.getTid()); // 對 ASE 使用原始 TID
            out.setIdDesc("ROBOT_MOVE_SCH_TO_EQP");

            R007AckPayload.Message m = new R007AckPayload.Message();
            m.setLotId(task.getLotId());
            m.setCarrierId(task.getCarrierId());
            m.setWipName(task.getWipName());
            m.setDestLoc(task.getDestLoc());
            m.setEqpPort(task.getEqpPort());
            m.setDeviceName(task.getDeviceName());
            // 若要回傳 STK_PORT 給 ASE，可視協定需要解除註解：
            // m.setStkPort(task.getStkPort());
            out.setMessage(m);

            out.setResult(externalResult);
            out.setResultMessage(resultMessage == null ? "" : resultMessage);

            // 先落 mqtt_message_log，再 publish
            JsonNode payload = objectMapper.valueToTree(out);
            logService.recordReturningId(
                    "ack/r007/forward",
                    logService.getLocalSystem(),   // sender: 我方
                    aseSystem,                     // receiver: ASE
                    payload,
                    MqttMessageType.ACK
            );

            publisher.publish(
                    aseSystem,
                    objectMapper.writeValueAsString(out),
                    MqttMessageType.ACK,
                    out.getTid(),
                    out.getCmdId()
            );

            log.info("[R007][ACK→ASE] 已轉傳：tid={}, result={}, receiver={}", out.getTid(), externalResult, aseSystem);
        } catch (Exception e) {
            // 轉傳失敗僅記錄錯誤，不影響內部任務狀態（以免外部暫時異常影響內部流程）
            log.error("[R007][ACK→ASE] 轉傳失敗：logId={}, err={}", task.getLogId(), e.getMessage(), e);
        }
    }

    // ======================= telemetry patch =======================

    /**
     * 從 ACK.Message 裡面把「遙測/參數」欄位回填到任務：
     *   - MISSION_TRIP（String）
     *   - ODO（BigDecimal）
     *   - AMR_SPEED（BigDecimal）
     *   - AMR_ROBOT_SPEED（BigDecimal）
     *
     * 僅在欄位有值時才回寫，避免覆蓋既有值；並不調整任務狀態。
     */
    private void updateTaskTelemetryFromAck(Long logId, R007AckPayload.Message msg) {
        if (msg == null) return;

        // 取值（保持原型別）
        String missionTrip = trimToNull(msg.getMissionTrip());
        BigDecimal odo = msg.getOdo();
        BigDecimal amrSpeed = msg.getAmrSpeed();
        BigDecimal amrRobotSpeed = msg.getAmrRobotSpeed();

        // 若四個欄位全為空，則略過
        boolean hasAny =
                missionTrip != null
                        ||  odo != null
                        ||  amrSpeed != null
                        ||  amrRobotSpeed != null;

        if (!hasAny) return;

        RobotR007Task patch = new RobotR007Task();
        patch.setLogId(logId);

        if (missionTrip != null)  patch.setMissionTrip(missionTrip);
        if (odo != null)          patch.setOdo(odo);
        if (amrSpeed != null)     patch.setAmrSpeed(amrSpeed);
        if (amrRobotSpeed != null)patch.setAmrRobotSpeed(amrRobotSpeed);

        patch.setUpdatedTime(LocalDateTime.now());
        boolean ok = taskRepo.updateByLogId(patch);
        if (!ok) {
            log.warn("[R007][ACK] Telemetry 回填失敗：logId={}", logId);
        } else {
            //log.debug("[R007][ACK] Telemetry 已回填：logId={}, missionTrip={}, odo={}, amrSpeed={}, amrRobotSpeed={}",
//                    logId, missionTrip, odo, amrSpeed, amrRobotSpeed);
        }
    }

    // ======================= helpers =======================

    /** 正規化 AMR 回傳的 result 值；修正常見拼寫錯誤（CANCLE → CANCEL） */
    private String normalizeResult(String r) {
        if (r == null) return "";
        r = r.trim().toUpperCase();
        if ("CANCLE".equals(r)) return "CANCEL"; // 對方拼字容錯
        return r;
    }

    /** 安全取得文字（null → 空字串） */
    private String safeText(String s) {
        return (s == null) ? "" : s.trim();
    }

    /** 取第一個非空白字串（若 a 為空則用 b） */
    private String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    /** 物件 → JSON 字串（失敗回 null） */
    private String safeToJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return null; }
    }

    /** 空白轉 null；字串有內容則回 trim 後的值 */
    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
