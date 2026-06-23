package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.util.BaseMqttHandlerUtils;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.dto.mqtt.command.R008CommandPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQuerySecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import com.czkuo.rdf88701.domain.repository.L005SessionRepository;
import com.czkuo.rdf88701.domain.repository.MqttInboxRepository;
import com.czkuo.rdf88701.domain.repository.RobotInR008Repository;
import com.czkuo.rdf88701.domain.repository.RobotR008TaskRepository;
import com.czkuo.rdf88701.infra.entity.L005Session;
import com.czkuo.rdf88701.infra.entity.RobotInR008;
import com.czkuo.rdf88701.infra.entity.RobotR008Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoR008Planner {

    private static final ZipTarget ZIP = ZipTarget.ZIPA; // 依現場：ZIPA/ZIPB
    private static final String OP1_PORT_NAME = "STK01-OP";
    private static final String OP2_PORT_NAME = "STK02-OP";

    private final ZipStockerCommandService zipService;

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;
    private final ObjectMapper objectMapper;

    private final RobotInR008Repository inRepo;
    private final RobotR008TaskRepository taskRepo;
    private final MqttInboxRepository inboxRepo;

    private final L005SessionRepository l005SessionRepository;

    /**
     * 建議 5~10 秒掃一次
     */
    //@Scheduled(fixedDelay = 5_000, initialDelay = 3_000)
    public void planR008IfEligible() {

        Root<StatusQuerySecondaryBody> inv = zipService.queryInventory(ZIP);
        if (inv == null || inv.getBody() == null || inv.getBody().getStatusInfos() == null) {
            //log.debug("[AutoR008] inventory 為空/不可解析");
            return;
        }

        String op1Carrier = toText(getCarrierIdFromInventory(inv, OP1_PORT_NAME));
        String op2Carrier = toText(getCarrierIdFromInventory(inv, OP2_PORT_NAME));

        if (hasCarrier(op1Carrier)) {
            if (tryCreateR008IfMissing(op1Carrier, "STK01")) return;
        }
        if (hasCarrier(op2Carrier)) {
            if (tryCreateR008IfMissing(op2Carrier, "STK02")) return;
        }

        //log.debug("[AutoR008] OP 口皆無可建單：op1={}, op2={}", op1Carrier, op2Carrier);
    }

    /**
     * 若該 carrier 尚未有 open 任務 → 建立一筆（一次只建一筆）
     */
    private boolean tryCreateR008IfMissing(String carrierId, String stkPort) {

        // ===== 1) 去重：對齊你 handler 的 findOngoingDuplicate()（open 任務 + 同 tid 或同 carrier）=====
        RobotR008Task dup = findOngoingDuplicateForAuto(carrierId /*autoTid is new each time*/);
        if (dup != null) {
            log.info("[AutoR008] skip duplicate: carrierId={} dupTid={} state={} taskId={}",
                    carrierId, dup.getTid(), dup.getInternalState(), dup.getId());
            return false;
        }

        // ===== 3) 組 command payload（完整 MESSAGE）=====
        R008CommandPayload cmd = buildAutoCommand(carrierId, stkPort);

        // 先做一次 validate（建議：防止產出自己都不合法的單）
        List<String> errs = R008Validation.validateForAuto(cmd, cmd.getMessage());
        if (!errs.isEmpty()) {
            log.warn("[AutoR008] validate fail: carrierId={} errs={}", carrierId, errs);
            return false;
        }

        // ===== 4) 落 mqtt_message_log（COMMAND）=====
        JsonNode payload = objectMapper.valueToTree(cmd);
        Long logId = logService.recordReturningId(
                "auto://r008",
                systemContext.getSystemCode(),
                systemContext.getSystemCode(),
                payload,
                MqttMessageType.COMMAND
        );

        // ===== 5) 落 robot_in_r008（冪等；PK=log_id）=====
        RobotInR008 row = new RobotInR008();
        row.setLogId(logId);
        row.setLotId(cmd.getMessage().getLotId());
        row.setCarrierId(cmd.getMessage().getCarrierId());
        row.setWipName(cmd.getMessage().getWipName());
        row.setDestLoc(cmd.getMessage().getDestLoc());
        row.setEqpPort(cmd.getMessage().getEqpPort());
        row.setDeviceName(cmd.getMessage().getDeviceName());
        row.setStkPort(cmd.getMessage().getStkPort());
        row.setCreatedTime(LocalDateTime.now());
        row.setUpdatedTime(LocalDateTime.now());

        if (inRepo.findById(logId).isPresent()) inRepo.update(row);
        else inRepo.save(row);

        // ===== 6) 落 robot_r008_task（對齊 handler）=====
        RobotR008Task task = new RobotR008Task();
        task.setLogId(logId);
        task.setInboxId(null);
        task.setTid(cmd.getTid());

        task.setLotId(cmd.getMessage().getLotId());
        task.setCarrierId(cmd.getMessage().getCarrierId());
        task.setWipName(cmd.getMessage().getWipName());
        task.setDestLoc(cmd.getMessage().getDestLoc());
        task.setEqpPort(cmd.getMessage().getEqpPort());
        task.setDeviceName(cmd.getMessage().getDeviceName());
        task.setStkPort(cmd.getMessage().getStkPort());

        // 其他欄位（Worker 常用）
        task.setTrayHigh(cmd.getMessage().getTrayHigh());
        task.setTrayType(cmd.getMessage().getTrayType());
        task.setBinType(cmd.getMessage().getBinType());
        task.setTrayNum(cmd.getMessage().getTrayNum());
        task.setMovePriority(cmd.getMessage().getMovePriority());
        task.setMissionTrip(cmd.getMessage().getMissionTrip());
        task.setOdo(cmd.getMessage().getOdo());
        task.setAmrSpeed(cmd.getMessage().getAmrSpeed());
        task.setAmrRobotSpeed(cmd.getMessage().getAmrRobotSpeed());
        task.setPpkgBodySize(cmd.getMessage().getPpkgBodySize());

        task.setInternalState("QUEUED");
        task.setExternalLastResult("OK");
        task.setExternalLastTime(LocalDateTime.now());
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());

        try {
            task.setRawMessageJson(objectMapper.writeValueAsString(cmd.getMessage()));
        } catch (Exception ignore) {
            task.setRawMessageJson(null);
        }

        if (taskRepo.findByLogId(logId).isPresent()) taskRepo.updateByLogId(task);
        else taskRepo.save(task);

        // ===== 7) 入 mqtt_inbox（priority mapping 對齊 handler）=====
        int priority = mapMovePriorityToInboxPriority(cmd.getMessage().getMovePriority());
        Long inboxId = inboxRepo.enqueueFromInbound(
                logId,
                cmd.getTid(),
                cmd.getCmdId(),
                systemContext.getSystemCode(),
                systemContext.getSystemCode(),
                "auto://r008",
                LocalDateTime.now(),
                priority
        );

        // ===== 8) 回填 inbox_id =====
        taskRepo.updateInboxIdByLogId(logId, inboxId);

        log.info("[AutoR008] created: logId={} inboxId={} carrierId={} stkPort={} trayType={} trayNum={} binType={}",
                logId, inboxId, carrierId, stkPort,
                cmd.getMessage().getTrayType(), cmd.getMessage().getTrayNum(), cmd.getMessage().getBinType());

        return true;
    }

    /**
     * 去重：對齊你 handler 的 findOngoingDuplicate 最終版（open 任務 + 同 carrier 擋）
     * （Auto 沒有上游重送 TID，所以 TID 比對可不做；但我保留接口讓你以後加）
     */
    private RobotR008Task findOngoingDuplicateForAuto(String carrierId) {
        List<RobotR008Task> open = taskRepo.findOpen();
        if (open == null || open.isEmpty()) return null;

        String inCarrier = toText(carrierId);
        for (RobotR008Task t : open) {
            if (safeEqIgnoreCase(inCarrier, t.getCarrierId())) return t;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────
    // build command (完整欄位)
    // ─────────────────────────────────────────────────────────────

    private R008CommandPayload buildAutoCommand(String carrierId, String stkPort) {
        Optional<L005Session> sOpt = l005SessionRepository.findLatestByPeerCarrierId(carrierId);
        if (sOpt.isEmpty()) return null;

        L005Session session = sOpt.get();

        R008CommandPayload p = new R008CommandPayload();
        p.setCmd("ROBOT");
        p.setCmdId("R008");
        p.setTid(BaseMqttHandlerUtils.generateTid());

        R008CommandPayload.Message m = new R008CommandPayload.Message();

        // 基本識別
        m.setLotId(session.getPeerLotId());
        m.setCarrierId(carrierId);

        // 目標/來源（依你現場定義；至少要有 DEST_LOC/EQP_PORT）
        m.setDestLoc(stkPort);
        m.setEqpPort(stkPort);

        m.setWipName(null);

        // 允許空
        m.setDeviceName(null);

        // 內部才會有：Auto 是你自己系統產，所以要帶
        // m.setStkPort(stkPort.equals("STK01") ? "STK04" : "STK03");
        m.setStkPort("STK04");

        m.setTrayType("4607996101");
        m.setTrayHigh(BigDecimal.valueOf(5.62));
        m.setTrayNum(21);
        m.setBinType("G");
        m.setPpkgBodySize("10x10x10");

        // 可選/但常用
        m.setMovePriority(null);
        m.setMissionTrip(null);
        m.setOdo(null);
        m.setAmrSpeed(null);
        m.setAmrRobotSpeed(null);

        p.setMessage(m);
        return p;
    }

    // ─────────────────────────────────────────────────────────────
    // inventory parsing：沿用你規則 Message[1]=CarrierID
    // ─────────────────────────────────────────────────────────────

    /** 從一次性的 type=6 inventory 回覆取 OP 口 carrier（Message[1] = CarrierID；null/空字串/"null" = 空位） */
    private String getCarrierIdFromInventory(Root<StatusQuerySecondaryBody> inv, String portName) {
        for (StatusQuerySecondaryBody.StatusInfo s : inv.getBody().getStatusInfos()) {
            if (s == null) continue;
            Integer type = toInt(s.getType());
            if (type == null || type != 6) continue;

            String name = toText(s.getName());
            if (name == null || !name.equalsIgnoreCase(portName)) continue;

            List<?> msg = s.getMessage(); // Message[0]=Barcode, Message[1]=CarrierID
            if (msg == null || msg.size() < 2) return "MSG_ERROR";

            String carrier = toText(msg.get(1));
            if ("null".equalsIgnoreCase(carrier)) return null; // 字串 "null" 視為空
            return carrier;
        }
        return null;
    }

    private boolean hasCarrier(String carrier) {
        carrier = toText(carrier);
        return carrier != null && !"null".equalsIgnoreCase(carrier);
    }

    // ─────────────────────────────────────────────────────────────
    // utils：copy from handler style
    // ─────────────────────────────────────────────────────────────

    private static int mapMovePriorityToInboxPriority(Integer mp) {
        Integer i = mp;
        if (i == null) return 5;
        return Math.max(1, Math.min(9, 10 - i)); // 越大越高 → 越接近 1
    }

    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString().trim()); } catch (Exception ignore) { return null; }
    }

    private static String toText(Object o) {
        if (o == null) return null;
        String s = Objects.toString(o, "").trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean safeEqIgnoreCase(String a, String b) {
        a = toText(a); b = toText(b);
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

    private static String nullSafe(String... arr) {
        if (arr == null) return null;
        for (String s : arr) if (s != null && !s.isBlank()) return s;
        return null;
    }

    private static String tryGet(Object obj, String getterName) {
        try {
            var m = obj.getClass().getMethod(getterName);
            Object v = m.invoke(obj);
            return (v != null) ? String.valueOf(v) : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String tryGetFromMessage1(Object statusInfo) {
        try {
            var m = statusInfo.getClass().getMethod("getMessage");
            Object msg = m.invoke(statusInfo);
            if (msg instanceof List<?> list) {
                if (list.size() > 1 && list.get(1) != null) return String.valueOf(list.get(1));
            }
            return null;
        } catch (Exception ignore) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 輕量 validate：只檢查 Auto 會填的必要欄位，避免產出自己都不合法的單
    // （你若想 100%沿用 handler validate，可把那段 validate 原封不動抽到共用類）
    // ─────────────────────────────────────────────────────────────
    static class R008Validation {
        static List<String> validateForAuto(R008CommandPayload cmd, R008CommandPayload.Message msg) {
            List<String> errs = new java.util.ArrayList<>();
            if (cmd == null) { errs.add("payload 為空"); return errs; }
            if (toText(cmd.getTid()) == null) errs.add("TID 缺失");
            if (msg == null) { errs.add("MESSAGE 缺失"); return errs; }

            if (toText(msg.getLotId()) == null) errs.add("LOT_ID 缺失");
            if (toText(msg.getCarrierId()) == null) errs.add("CARRIERID 缺失");
            if (toText(msg.getDestLoc()) == null) errs.add("DEST_LOC 缺失");
            if (toText(msg.getEqpPort()) == null) errs.add("EQP_PORT 缺失");

            if (toText(msg.getTrayType()) == null) errs.add("TRAY_TYPE 缺失");

            BigDecimal h = msg.getTrayHigh();
            if (h == null) errs.add("TRAY_HIGH 缺失");
            else if (h.compareTo(BigDecimal.ZERO) <= 0) errs.add("TRAY_HIGH 必須 > 0");

            Integer n = msg.getTrayNum();
            if (n == null) errs.add("TRAY_NUM 缺失");
            else if (n < 0) errs.add("TRAY_NUM 不可負數");

            String bin = toText(msg.getBinType());
            if (bin == null) errs.add("BIN_TYPE 缺失");

            String pbs = toText(msg.getPpkgBodySize());
            if (pbs == null) errs.add("PPKG_BODY_SIZE 缺失");

            return errs;
        }
    }
}
