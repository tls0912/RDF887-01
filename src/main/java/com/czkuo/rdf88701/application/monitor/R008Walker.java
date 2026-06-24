package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R008AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.R008CommandPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.repository.MqttInboxRepository;
import com.czkuo.rdf88701.domain.repository.RobotInR008Repository;
import com.czkuo.rdf88701.domain.repository.RobotR008TaskRepository;
import com.czkuo.rdf88701.infra.entity.MqttInbox;
import com.czkuo.rdf88701.infra.entity.RobotInR008;
import com.czkuo.rdf88701.infra.entity.RobotR008Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;


/**
 * R008Walker (one-shot + enrich from R008 task)
 * - 只發一次 R008 給 SEEC（沿用入站 TID）
 * - 不修改任何資料表（不寫 task、不更新 inbox 以外的表）
 * - MESSAGE 欄位優先採用 robot_r008_task 的值，缺才回退 robot_in_r008
 * - STK_PORT：優先 task.stkPort；否則固定 "STK03"
 * - 送出後立即回 START，然後把 inbox 標記 DONE
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class R008Walker {

    private final MqttInboxRepository inboxRepo;
    private final RobotInR008Repository r008Repo;
    private final RobotR008TaskRepository taskRepo;
    private final MqttMessageEventPublisher publisher;
    private final MqttMessageLogService logService;
    private final ObjectMapper objectMapper;

    @Value("${app.worker.r008.enabled:true}")
    private boolean enabled;

    @Value("${app.worker.r008.lock-ttl-seconds:60}")
    private int lockTtlSeconds;

    @Value("${app.worker.r008.interval-ms:500}")
    private long intervalMs;

    @Value("${spring.application.name:r008-worker}")
    private String workerId;

    @Scheduled(fixedDelayString = "${app.worker.r008.interval-ms:800}")
    public void tick() {
        if (!enabled) return;
        processOnce();
    }

    public void processOnce() {
        Optional<MqttInbox> opt = inboxRepo.pickOneForProcessingByCmdNoNextAttemptTime("R008", workerId, Duration.ofSeconds(lockTtlSeconds));
        if (opt.isEmpty()) return;

        MqttInbox inbox = opt.get();
        Long inboxId = inbox.getId();
        Long logId = inbox.getLogId();

        try {
            if (!"R008".equalsIgnoreCase(inbox.getCmdId())) {
                inboxRepo.requeue(inboxId, Duration.ofSeconds(1));
                return;
            }

            // 必要：入站明細
            var mOpt = r008Repo.findById(logId);
            if (mOpt.isEmpty()) {
                String reason = "robot_in_r008 not found, logId=" + logId;
                inboxRepo.markRejected(inboxId, reason);
                log.error("[R008][one-shot] {}；inboxId={} → REJECTED", reason, inboxId);
                return;
            }
            RobotInR008 m = mOpt.get();

            // 只讀 task：用來 enrich（可無）
            RobotR008Task task = taskRepo.findByLogId(logId).orElse(null);

            // 組一次性 R008（沿用入站 TID）
            R008CommandPayload forward = new R008CommandPayload();
            forward.setCmd("ROBOT");
            forward.setCmdId("R008");
            forward.setTid(inbox.getTid());
            forward.setIdDesc("ROBOT_MOVE_EQP_TO_SCH");
            forward.setResult("");
            forward.setResultMessage("");

            R008CommandPayload.Message mm = new R008CommandPayload.Message();

            // 核心欄位：task 優先，無則回退 m（入站）
            mm.setLotId(nvl(task != null ? task.getLotId() : null, m.getLotId()));
            mm.setCarrierId(nvl(task != null ? task.getCarrierId() : null, m.getCarrierId()));
            mm.setWipName(nvl(task != null ? task.getWipName() : null, m.getWipName()));       // 目標 WIP/STK
            mm.setDestLoc(nvl(task != null ? task.getDestLoc() : null, m.getDestLoc()));       // 來源 EQP（你的命名為 destLoc）
            mm.setEqpPort(nvl(task != null ? task.getEqpPort() : null, m.getEqpPort()));
            mm.setDeviceName(nvl(task != null ? task.getDeviceName() : null, m.getDeviceName()));

            // 先決定 binType（僅從 task 取，沒有就 null）
            String taskBinType = task != null ? task.getBinType() : null;

            // STK_PORT 規則：B → STK05；其餘維持原本（task.stkPort 優先，其次 STK03）
            String stkPort;
            if ("B".equals(taskBinType)) {
                stkPort = "STK05";
            } else {
                stkPort = (task != null && notBlank(task.getStkPort())) ? task.getStkPort() : "STK03";
                //stkPort = "STK04";
            }
            mm.setStkPort(stkPort);

            // 擴充欄位：全取自 task（存在才帶）
            if (task != null) {
                if (task.getTrayHigh()      != null) mm.setTrayHigh(task.getTrayHigh());
                if (task.getTrayType()      != null) mm.setTrayType(task.getTrayType());
                if (task.getBinType()       != null) mm.setBinType(task.getBinType());
                if (task.getTrayNum()       != null) mm.setTrayNum(task.getTrayNum());
                if (task.getMovePriority()  != null) mm.setMovePriority(task.getMovePriority());
                // if (task.getMissionTrip()   != null) mm.setMissionTrip(task.getMissionTrip().toString());
                // if (task.getOdo()           != null) mm.setOdo(task.getOdo());
                // if (task.getAmrSpeed()      != null) mm.setAmrSpeed(task.getAmrSpeed());
                // if (task.getAmrRobotSpeed() != null) mm.setAmrRobotSpeed(task.getAmrRobotSpeed());
                if (task.getPpkgBodySize()  != null) mm.setPpkgBodySize(task.getPpkgBodySize());
            }

            forward.setMessage(mm);

            // 記 log → 發 COMMAND（一次性）
            logService.recordReturningId(
                    "cmd/r008/forward-one-shot",
                    workerId, "seec",
                    objectMapper.valueToTree(forward),
                    MqttMessageType.COMMAND
            );

            publisher.publish(
                    "seec",
                    objectMapper.writeValueAsString(forward),
                    MqttMessageType.COMMAND,
                    forward.getTid(),
                    forward.getCmdId()
            );

            // 回 START（僅一次）
            // sendAckStart(inbox.getSender(), inbox.getTid(), m);

            // 結案（不等待 AMR ACK、也不改任一 task 資料）
            inboxRepo.markDone(inboxId, "R008", null);
            log.info("[R008][one-shot] forwarded with task-enriched message & START ack sent, inbox DONE. tid={}", inbox.getTid());

        } catch (Exception e) {
            log.error("[R008][one-shot] 例外，requeue；inboxId={}, err={}", inboxId, e.getMessage(), e);
            inboxRepo.requeue(inboxId, Duration.ofSeconds(10));
        }
    }

    // --- ACK：只回 START ---

    private void sendAckStart(String targetSystem, String tid, RobotInR008 m) throws Exception {
        R008AckPayload ack = new R008AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R008");
        ack.setTid(tid);
        ack.setIdDesc("ROBOT_MOVE_EQP_TO_SCH");

        R008AckPayload.Message msg = new R008AckPayload.Message();
        msg.setLotId(m.getLotId());
        msg.setCarrierId(m.getCarrierId());
        msg.setWipName(m.getWipName());
        msg.setDestLoc(m.getDestLoc());
        msg.setEqpPort(m.getEqpPort());
        msg.setDeviceName(m.getDeviceName());
        ack.setMessage(msg);

        ack.setResult("START");
        ack.setResultMessage("");

        logService.recordReturningId(
                "ack/r008/start",
                workerId, targetSystem,
                objectMapper.valueToTree(ack),
                MqttMessageType.ACK
        );

        publisher.publish(
                targetSystem,
                objectMapper.writeValueAsString(ack),
                MqttMessageType.ACK,
                ack.getTid(),
                ack.getCmdId()
        );
    }

    // --- helpers ---

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private static <T> T nvl(T a, T b) { return a != null ? a : b; }
}

