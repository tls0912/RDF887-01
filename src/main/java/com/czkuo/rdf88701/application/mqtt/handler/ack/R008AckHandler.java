package com.czkuo.rdf88701.application.mqtt.handler.ack;


import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R008AckPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.repository.RobotR008TaskRepository;
import com.czkuo.rdf88701.infra.entity.RobotR008Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;


/**
 * R008AckHandler
 * - 處理 CMD_ID=R008 的 ACK（EQP → WIP(STK)）
 * - 流程：
 *   1) 記錄 ACK 至 mqtt_message_log
 *   2) 以 TID 反查 robot_r008_task（找不到則告警後結束）
 *   3) 回填 Telemetry：MISSION_TRIP / ODO / AMR_SPEED / AMR_ROBOT_SPEED（僅有值才 patch）
 *   4) 寫入 external_last_result / external_last_time（並於 FAIL/CANCEL 寫 fail_reason/cancel_reason）
 *   5) （可選）END/FAIL/CANCEL 轉傳給 ASE（開關：app.external.forward-r008-enabled）
 */
@Slf4j
@Component
public class R008AckHandler extends AbstractAckHandler<R008AckPayload> {

    private final MqttMessageLogService logService;
    private final RobotR008TaskRepository taskRepo;
    private final MqttMessageEventPublisher publisher;

    @Value("${app.external.forward-r008-enabled:true}")
    private boolean forwardEnabled;

    /** 轉傳目的系統（預設 ase） */
    @Value("${app.external.ase-system:ase}")
    private String aseSystem;

    public R008AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService,
                          RobotR008TaskRepository taskRepo,
                          MqttMessageEventPublisher publisher) {
        super(objectMapper);
        this.logService = logService;
        this.taskRepo = taskRepo;
        this.publisher = publisher;
    }

    @Override
    protected void process(String system, String topic, R008AckPayload ack) throws Exception {
        final String tid       = safeText(ack.getTid());          // 任務 TID（R008 one-shot 沿用原 TID）
        final String resultRaw = safeText(ack.getResult());
        final String result    = normalizeResult(resultRaw);      // 修正常見錯拼：CANCLE→CANCEL
        final String resultMsg = safeText(ack.getResultMessage());

        // 1) 記錄 ACK
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        Long ackLogId = logService.recordReturningId(
                topic,
                system,                         // sender：對方
                logService.getLocalSystem(),    // receiver：我方
                jsonPayload,
                MqttMessageType.ACK
        );

        // 2) 以 TID 反查任務
        Optional<RobotR008Task> opt = taskRepo.findLatestByTid(tid);
        if (opt.isEmpty()) {
            log.error("[R008][ACK] 找不到對應任務：tid={}, result={}, topic={}, system={}", tid, result, topic, system);
            return;
        }
        RobotR008Task task = opt.get();

        // 先拿 Message 出來，順便判斷 STK_PORT
        R008AckPayload.Message msg = ack.getMessage();
        String stkPort = (msg != null) ? trimToNull(msg.getStkPort()) : null;
        boolean fromStk05 = "STK05".equalsIgnoreCase(stkPort);

        // 3) 先回填 Telemetry（僅 patch 有值欄位；不受 result 類型限制）
        try { updateTaskTelemetryFromAck(task.getLogId(), msg); }
        catch (Exception teleEx) {
            log.warn("[R008][ACK] Telemetry 回填失敗：logId={}, err={}", task.getLogId(), teleEx.getMessage(), teleEx);
        }

        // 4) START / FAIL / CANCEL / (STK05 的 END) 寫 external_*
        try {
            boolean isFinalForDb =
                    "START".equals(result) ||
                            "FAIL".equals(result) ||
                            "CANCEL".equals(result) ||
                            "END".equals(result);

            if (isFinalForDb) {
                RobotR008Task patch = new RobotR008Task();
                patch.setLogId(task.getLogId());
                patch.setExternalLastResult(result);
                patch.setExternalLastTime(LocalDateTime.now());

                if ("FAIL".equals(result)) {
                    patch.setFailReason(firstNonBlank(resultMsg, "AMR 回覆 FAIL"));
                } else if ("CANCEL".equals(result)) {
                    patch.setCancelReason(firstNonBlank(resultMsg, "AMR 取消任務"));
                }

                patch.setUpdatedTime(LocalDateTime.now());

                boolean ok = taskRepo.updateByLogId(patch);
                if (!ok) {
                    log.warn("[R008][ACK] 更新 external result 失敗：tid={}, result={}", tid, result);
                }
            } else {
                // OK / END(非 STK05)：不寫 DB（僅記錄 log 與 Telemetry）
                //log.debug("[R008][ACK] 跳過 external_* 寫入：tid={}, result={}, stkPort={}", tid, result, stkPort);
            }
        } catch (Exception e) {
            log.warn("[R008][ACK] 寫 external result 異常：tid={}, err={}", tid, e.getMessage(), e);
        }

        // 5) 可選：START / FAIL / CANCEL / (STK05 的 END) 轉傳給 ASE（用任務原始 TID）
        boolean needForward =
                "START".equals(result) ||
                        "FAIL".equals(result) ||
                        "CANCEL".equals(result) ||
                        "END".equals(result);

        if (forwardEnabled && needForward) {
            forwardFinalToAse(task, result, resultMsg);
        }

        log.info("[R008][ACK] 處理完成：tid={}, result={}, stkPort={}, ackLogId={}",
                tid, result, stkPort, ackLogId);
    }

    @Override
    protected String getCmdIdInternal() { return "R008"; }

    @Override
    protected Class<R008AckPayload> getAckType() { return R008AckPayload.class; }

    // ======================= telemetry patch =======================

    private void updateTaskTelemetryFromAck(Long logId, R008AckPayload.Message msg) {
        if (msg == null) return;

        // 你把 mission_trip 改成 varchar(64)，這裡直接當字串處理
        String missionTrip      = trimToNull(msg.getMissionTrip());
        var    odo              = msg.getOdo();
        var    amrSpeed         = msg.getAmrSpeed();
        var    amrRobotSpeed    = msg.getAmrRobotSpeed();

        boolean hasAny = missionTrip != null || odo != null || amrSpeed != null || amrRobotSpeed != null;
        if (!hasAny) return;

        RobotR008Task patch = new RobotR008Task();
        patch.setLogId(logId);
        if (missionTrip   != null) patch.setMissionTrip(missionTrip);
        if (odo           != null) patch.setOdo(odo);
        if (amrSpeed      != null) patch.setAmrSpeed(amrSpeed);
        if (amrRobotSpeed != null) patch.setAmrRobotSpeed(amrRobotSpeed);
        patch.setUpdatedTime(LocalDateTime.now());

        boolean ok = taskRepo.updateByLogId(patch);
        if (!ok) {
            log.warn("[R008][ACK] Telemetry 回填失敗：logId={}", logId);
        } else {
            //log.debug("[R008][ACK] Telemetry 已回填：logId={}, missionTrip={}, odo={}, amrSpeed={}, amrRobotSpeed={}",
//                    logId, missionTrip, odo, amrSpeed, amrRobotSpeed);
        }
    }

    // ======================= forward to ASE (optional) =======================

    private void forwardFinalToAse(RobotR008Task task, String externalResult, String resultMessage) {
        try {
            // 組 ACK 給 ASE（以任務原始 TID 上報）
            R008AckPayload out = new R008AckPayload();
            out.setCmd("ROBOT");
            out.setCmdId("R008");
            out.setTid(task.getTid());
            out.setIdDesc("ROBOT_MOVE_EQP_TO_SCH");

            R008AckPayload.Message m = new R008AckPayload.Message();
            m.setLotId(m.getLotId());
            m.setCarrierId(m.getCarrierId());
            m.setWipName(m.getWipName());
            m.setDestLoc(m.getDestLoc());
            m.setEqpPort(m.getEqpPort());
            m.setTrayHigh(m.getTrayHigh());
            m.setTrayType(m.getTrayType());
            m.setBinType(m.getBinType());
            m.setTrayNum(m.getTrayNum());
            m.setDeviceName(m.getDeviceName());
            m.setMovePriority(m.getMovePriority());
            m.setMissionTrip(m.getMissionTrip());
            m.setOdo(m.getOdo());
            m.setAmrSpeed(m.getAmrSpeed());
            m.setAmrRobotSpeed(m.getAmrRobotSpeed());
            m.setPpkgBodySize(m.getPpkgBodySize());
            // R008 對 ASE 不需回傳 STK_PORT，若需要可依協定加上
            out.setMessage(m);

            out.setResult(externalResult);
            out.setResultMessage(resultMessage == null ? "" : resultMessage);

            JsonNode payload = objectMapper.valueToTree(out);
            logService.recordReturningId(
                    "ack/r008/forward",
                    logService.getLocalSystem(),   // sender：我方
                    aseSystem,                     // receiver：ASE
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

            log.info("[R008][ACK→ASE] 已轉傳：tid={}, result={}, receiver={}", out.getTid(), externalResult, aseSystem);
        } catch (Exception e) {
            log.error("[R008][ACK→ASE] 轉傳失敗：logId={}, err={}", task.getLogId(), e.getMessage(), e);
        }
    }

    // ======================= helpers =======================

    private String normalizeResult(String r) {
        if (r == null) return "";
        r = r.trim().toUpperCase();
        if ("CANCLE".equals(r)) return "CANCEL";
        return r;
    }

    private String resultOrNull(String r) {
        if (r == null) return null;
        String x = r.trim().toUpperCase();
        return x.isEmpty() ? null : x;
    }

    private String safeText(String s) { return (s == null) ? "" : s.trim(); }

    private String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
