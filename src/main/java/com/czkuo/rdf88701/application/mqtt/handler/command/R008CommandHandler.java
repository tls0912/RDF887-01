package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R008AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.R008CommandPayload;
import com.czkuo.rdf88701.domain.repository.MqttInboxRepository;
import com.czkuo.rdf88701.domain.repository.RobotInR008Repository;
import com.czkuo.rdf88701.domain.repository.RobotR008TaskRepository;
import com.czkuo.rdf88701.infra.entity.RobotInR008;
import com.czkuo.rdf88701.infra.entity.RobotR008Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * R008CommandHandler
 * - 入站 CMD_ID = R008（EQP → WIP/STK）
 * - 流程：
 *   1) 記錄 COMMAND 至 mqtt_message_log（取得 logId）
 *   2) 落地 MESSAGE 至 robot_in_r008（PK=log_id）
 *   3) 匯入 mqtt_inbox 佇列（RECEIVED），交由 R008 Worker 後續處理
 *   4) 立即回 ACK（RESULT=OK）
 *
 * 備註：
 * - 若 sender=ASE，按規格 MESSAGE 不應帶 STK_PORT；此處僅記錄不阻擋流程。
 */
@Slf4j
@Component
public class R008CommandHandler extends AbstractCommandHandler<R008CommandPayload> {

    private final MqttMessageLogService logService;        // 寫 mqtt_message_log（須支援回傳 id）
    private final SystemContext systemContext;             // 本系統代碼
    private final RobotInR008Repository r008Repository;    // inbound 明細表（robot_in_r008）
    private final RobotR008TaskRepository taskRepository;  // 任務主表（robot_r008_task）
    private final MqttInboxRepository inboxRepository;     // 入站佇列（mqtt_inbox）

    public R008CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              RobotInR008Repository r008Repository,
                              RobotR008TaskRepository taskRepository,
                              MqttInboxRepository inboxRepository) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.r008Repository = r008Repository;
        this.taskRepository = taskRepository;
        this.inboxRepository = inboxRepository;
    }

    /**
     * 主處理流程
     *
     * @param system  來源系統（如 SAA/ASE）
     * @param topic   MQTT topic
     * @param command 已反序列化的 R008CommandPayload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, R008CommandPayload command, MqttMessageType type) throws Exception {
        // ===== 0) 標準化與驗證 =====
        final R008CommandPayload.Message raw = (command != null) ? command.getMessage() : null;
        final R008CommandPayload.Message msg = normalizeMessage(raw); // trim、空白→null

        List<String> errors = validate(system, command, msg);
        if (!errors.isEmpty()) {
            // 還未落 log 時無 logId，但仍應回 ACK=FAIL
            replyAck(system, command, echoAckMessage(msg, /*echoStkPort*/ false), "FAIL", String.join("; ", errors));
            log.warn("[R008] 參數驗證失敗：tid={}, errors={}", (command != null ? command.getTid() : null), errors);
            return;
        }

        final String tid       = command.getTid();
        final String lotId     = msg.getLotId();
        final String carrierId = msg.getCarrierId();
        final String stkPort   = msg.getStkPort();

        log.info("[R008] 收到指令：tid={}, topic={}, sender={}, lot={}, carrier={}, stkPort={}",
                tid, topic, system, lotId, carrierId, stkPort);

        // ===== 1) 記錄 COMMAND 至 mqtt_message_log（回傳 logId；不論後續成功/失敗都先落地稽核）=====
        final JsonNode payload = objectMapper.valueToTree(command);
        final Long logId = logService.recordReturningId(
                topic,
                system,                        // sender：對方
                systemContext.getSystemCode(), // receiver：本系統
                payload,
                MqttMessageType.COMMAND
        );

        try {
            // ===== 2) 防重複任務檢查（只看進行中 open 任務）=====
            RobotR008Task dup = findOngoingDuplicate(msg, tid);
            if (dup != null) {
                // 標記追蹤資訊（可忽略失敗）
                // try {
                //     dup.setExternalLastResult("FAIL");
                //     dup.setFailReason("偵測到重複任務");
                //     dup.setExternalLastTime(LocalDateTime.now());
                //     taskRepository.update(dup);
                // } catch (Exception ignore) {
                //     //log.debug("[R008] 更新既有任務 external_last_* 失敗（可忽略） tid={}", tid);
                // }

                boolean echoStk = !"ase".equalsIgnoreCase(system);
                replyAck(system, command, echoAckMessage(msg, echoStk),
                        "FAIL", "DUPLICATE: task already exists (tid=" + dup.getTid() + ")");
                log.warn("[R008] 偵測到重複任務：tid={} carrierId={} destLoc={} eqpPort={} stkPort={} 已存在任務 id={}, internal_state={}",
                        tid, msg.getCarrierId(), msg.getDestLoc(), msg.getEqpPort(), msg.getStkPort(),
                        dup.getId(), dup.getInternalState());
                return; // 不落地、不入佇列
            }

            // ===== 3~6) 交易性區段 =====
            Long inboxId = transactionalInboundAndEnqueue(system, topic, command, msg, logId);

            // ===== 7) 回 ACK（RESULT=OK）=====
            boolean echoStk = !"ase".equalsIgnoreCase(system);
            replyAck(system, command, echoAckMessage(msg, echoStk), "OK", "");
            log.info("[R008] 成功：logId={}，inboxId={}，已回 ACK=OK。", logId, inboxId);

        } catch (DataAccessException dae) {
            String reason = "DB 錯誤：" + safeMsg(dae);
            replyAck(system, command, echoAckMessage(msg, false), "FAIL", reason);
            log.error("[R008] {}", reason, dae);
        } catch (Exception ex) {
            String reason = "未預期錯誤：" + safeMsg(ex);
            replyAck(system, command, echoAckMessage(msg, false), "FAIL", reason);
            log.error("[R008] {}", reason, ex);
        }
    }

    /** 交易性：落 inbound → upsert 任務 → 入佇列 → 回填 inbox_id */
    @Transactional
    protected Long transactionalInboundAndEnqueue(String system,
                                                  String topic,
                                                  R008CommandPayload command,
                                                  R008CommandPayload.Message msg,
                                                  Long logId) {
        // 2) inbound：robot_in_r008（冪等）
        RobotInR008 row = new RobotInR008();
        row.setLogId(logId);                // PK = mqtt_message_log.id
        row.setLotId(msg.getLotId());
        row.setCarrierId(msg.getCarrierId());
        row.setWipName(msg.getWipName());        // 可為 null
        row.setDestLoc(msg.getDestLoc());
        row.setEqpPort(msg.getEqpPort());
        row.setDeviceName(msg.getDeviceName());  // 可為 null/空字串
        row.setStkPort(msg.getStkPort());        // SAA→SEEC 才會有

        if (r008Repository.findById(logId).isPresent()) {
            r008Repository.update(row);
        } else {
            r008Repository.save(row);
        }

        // 3) upsert 任務：robot_r008_task（以 log_id 冪等；數值轉換）
        RobotR008Task task = new RobotR008Task();
        task.setLogId(logId);
        task.setInboxId(null);
        task.setTid(command.getTid());

        task.setLotId(msg.getLotId());
        task.setCarrierId(msg.getCarrierId());
        task.setWipName(msg.getWipName());
        task.setDestLoc(msg.getDestLoc());
        task.setEqpPort(msg.getEqpPort());
        task.setDeviceName(msg.getDeviceName());
        task.setStkPort(msg.getStkPort());

        // 其他欄位
        task.setTrayHigh(msg.getTrayHigh());
        task.setTrayType(msg.getTrayType());
        task.setBinType(msg.getBinType());
        task.setTrayNum(msg.getTrayNum());                      // Integer
        task.setMovePriority(msg.getMovePriority()); // "1" → 1
        task.setMissionTrip(msg.getMissionTrip());
        task.setOdo(msg.getOdo());
        task.setAmrSpeed(msg.getAmrSpeed());
        task.setAmrRobotSpeed(msg.getAmrRobotSpeed());
        task.setPpkgBodySize(msg.getPpkgBodySize());

        // 狀態與快取（接單成功 → 佇列中）
        task.setInternalState("QUEUED");
        task.setExternalLastResult("OK");
        task.setExternalLastTime(LocalDateTime.now());
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());

        try {
            task.setRawMessageJson(objectMapper.writeValueAsString(msg));
        } catch (Exception ignore) {
            task.setRawMessageJson(null);
        }

        if (taskRepository.findByLogId(logId).isPresent()) {
            taskRepository.updateByLogId(task);
        } else {
            taskRepository.save(task);
        }

        // 4) 入佇列：mqtt_inbox（priority 由 MOVE_PRIORITY 映射）
        int priority = mapMovePriorityToInboxPriority(msg.getMovePriority()); // 1 高→9 低
        Long inboxId = inboxRepository.enqueueFromInbound(
                logId,
                command.getTid(),
                command.getCmdId(),
                system,                        // sender
                systemContext.getSystemCode(), // receiver
                topic,
                LocalDateTime.now(),
                priority
        );

        // 5) 回填任務的 inbox_id
        taskRepository.updateInboxIdByLogId(logId, inboxId);
        return inboxId;
    }

    /** Router 用 CMD_ID */
    @Override protected String getCmdIdInternal() { return "R008"; }

    /** Jackson 反序列化型別 */
    @Override protected Class<R008CommandPayload> getCommandType() { return R008CommandPayload.class; }

    // ===================== 防重複 / 標準化 / 驗證 / ECHO / UTIL =====================

    /**
     * 判斷是否有「進行中」且重複的 R008 任務：
     *  - 冪等條件1：相同 TID（合作方重送/重試）
     *  - 冪等條件2：相同關鍵組合（CARRIERID + DEST_LOC + EQP_PORT [+ STK_PORT]），且仍未結案
     *    * 若入站 MESSAGE 帶 STK_PORT，則一併要求相等；未帶則只比對前三者以避免誤擋
     *  - 僅檢查 open 任務（由 repository.findOpen() 負責過濾）
     */
    private RobotR008Task findOngoingDuplicate(R008CommandPayload.Message msg, String tid) {
        if (msg == null) return null;

        String inCarrier = toText(msg.getCarrierId());
        String inDest    = toText(msg.getDestLoc());
        String inEqp     = toText(msg.getEqpPort());
        String inTid     = toText(tid);

        List<RobotR008Task> openTasks = taskRepository.findOpen();
        for (RobotR008Task t : openTasks) {
            // 條件1：同 TID（最強冪等）
            if (inTid != null && inTid.equalsIgnoreCase(toText(t.getTid()))) {
                return t;
            }

            // 條件2：同 (carrierId + destLoc + eqpPort)
            // boolean sameCarrier = safeEqIgnoreCase(inCarrier, t.getCarrierId());
            // boolean sameDest    = safeEqIgnoreCase(inDest,    t.getDestLoc());
            // boolean sameEqp     = safeEqIgnoreCase(inEqp,     t.getEqpPort());
            // if (sameCarrier && sameDest && sameEqp) {
            //     return t;
            // }

            boolean sameCarrier = safeEqIgnoreCase(inCarrier, t.getCarrierId());
            if (sameCarrier) {
                return t;
            }
        }
        return null;
    }

    /** 標準化：trim 文字、全空白→null；保留 DEVICE_NAME 可為空字串；其他欄位按協定處理 */
    private static R008CommandPayload.Message normalizeMessage(R008CommandPayload.Message src) {
        if (src == null) return null;
        R008CommandPayload.Message m = new R008CommandPayload.Message();

        m.setLotId(toText(src.getLotId()));
        m.setCarrierId(toText(src.getCarrierId()));
        m.setWipName(toText(src.getWipName()));           // 可為 null
        m.setDestLoc(toText(src.getDestLoc()));
        m.setEqpPort(toText(src.getEqpPort()));

        // DEVICE_NAME 允許 "" 或 null：這裡僅做 trim，不強制轉 null
        String dn = src.getDeviceName();
        m.setDeviceName(dn == null ? null : dn.trim());

        // 數值字串：保留原樣，驗證時再檢核格式
        m.setTrayHigh(src.getTrayHigh());
        m.setTrayType(toText(src.getTrayType()));
        m.setBinType(toText(src.getBinType()));
        m.setTrayNum(src.getTrayNum());                    // Integer：保留

        m.setMovePriority(src.getMovePriority());          // 整數字串（可選）
        m.setMissionTrip(toText(src.getMissionTrip()));    // 數值字串（可選）
        m.setOdo(src.getOdo());                            // 數值字串（可選）
        m.setAmrSpeed(src.getAmrSpeed());                  // 數值字串（可選）
        m.setAmrRobotSpeed(src.getAmrRobotSpeed());        // 數值字串（可選）
        m.setPpkgBodySize(toText(src.getPpkgBodySize()));

        m.setStkPort(toText(src.getStkPort()));           // 場景規則另行檢核
        return m;
    }

    /**
     * 欄位驗證：
     * - 必填：LOT_ID, CARRIERID, DEST_LOC, EQP_PORT, TRAY_TYPE, TRAY_HIGH, TRAY_NUM
     * - TRAY_HIGH：必須為數值字串且 >=0；小數位 ≤3（建議）
     * - TRAY_NUM：整數 >=0
     * - MOVE_PRIORITY：若有值→整數字串（建議 0~9）
     * - STK_PORT：SAA→SEEC 必填；ASE→廠商 禁止帶
     */
    private static List<String> validate(String system, R008CommandPayload cmd, R008CommandPayload.Message msg) {
        List<String> errs = new ArrayList<>();
        if (cmd == null) { errs.add("payload 為空"); return errs; }
        if (toText(cmd.getTid()) == null) errs.add("TID 缺失");
        if (msg == null) { errs.add("MESSAGE 缺失"); return errs; }

        if (toText(msg.getLotId()) == null)       errs.add("LOT_ID 缺失");
        if (toText(msg.getCarrierId()) == null)   errs.add("CARRIERID 缺失");
        if (toText(msg.getDestLoc()) == null)     errs.add("DEST_LOC 缺失");
        if (toText(msg.getEqpPort()) == null)     errs.add("EQP_PORT 缺失");
        // wipName 可為空或 null，不驗證
        // deviceName 可為空或 null，不驗證

        // TRAY_TYPE（料號）：必填；長度 ≤ 64
        String trayType = msg.getTrayType();
        if (trayType == null) {
            errs.add("TRAY_TYPE 缺失");
        } else if (trayType.length() > 64) {
            errs.add("TRAY_TYPE 長度超過 64");
        }

        // TRAY_HIGH：必填；>0 且小數位 ≤ 3
        if (msg.getTrayHigh() == null) {
            errs.add("TRAY_HIGH 缺失");
        } else {
            try {
                BigDecimal h = msg.getTrayHigh();
                if (h.compareTo(BigDecimal.ZERO) <= 0) {  // 必須 > 0
                    errs.add("TRAY_HIGH 必須大於 0");
                } else if (h.scale() > 3) {
                    errs.add("TRAY_HIGH 小數位不可超過 3（目前=" + h.scale() + "）");
                }
            } catch (Exception e) {
                errs.add("TRAY_HIGH 非法數值");
            }
        }

        // TRAY_NUM：必填；整數 >=0
        Integer n = msg.getTrayNum();
        if (n == null) {
            errs.add("TRAY_NUM 缺失");
        } else if (n < 0) {
            errs.add("TRAY_NUM 不可為負數");
        }

        // MOVE_PRIORITY：可選；若有值需為整數字串（建議 0~9）
        Integer mp = msg.getMovePriority();
        if (mp != null) {
            // Integer mpi = parseInt(mp);
            Integer mpi = mp;
            if (mpi == null) {
                errs.add("MOVE_PRIORITY 非法整數");
            } else if (mpi < 0 || mpi > 9) {
                errs.add("MOVE_PRIORITY 僅允許 0~9");
            }
        }

        // BIN_TYPE：必填；允許 G/B/E 或 GOOD/BAD/EMPTY
        String binType = toText(msg.getBinType());
        if (binType == null) {
            errs.add("BIN_TYPE 缺失");
        } else if (!isValidBinType(binType)) {
            errs.add("BIN_TYPE 僅允許 G/B/E 或 GOOD/BAD/EMPTY（目前=" + binType + "）");
        }

        // PPKG_BODY_SIZE（可選；長度 ≤32；字元白名單）
        String pbs =  msg.getPpkgBodySize();
        if (pbs == null) {
            errs.add("PPKG_BODY_SIZE 缺失");
        } else {
            if (pbs.length() > 32) {
                errs.add("PPKG_BODY_SIZE 長度超過 32");
            } else if (!pbs.matches("^[A-Za-z0-9._xX\\-]+$")) {
                errs.add("PPKG_BODY_SIZE 僅允許英數與 . _ - x/X");
            }
        }

        return errs;
    }

    /** 以目前約定 echo 回去的 MESSAGE（預設 ASE 不回 STK_PORT） */
    private static R008AckPayload.Message echoAckMessage(R008CommandPayload.Message msg, boolean echoStkPort) {
        R008AckPayload.Message m = new R008AckPayload.Message();
        if (msg == null) return m;
        m.setLotId(msg.getLotId());
        m.setCarrierId(msg.getCarrierId());
        m.setWipName(msg.getWipName());
        m.setDestLoc(msg.getDestLoc());
        m.setEqpPort(msg.getEqpPort());
        m.setDeviceName(msg.getDeviceName());
        m.setStkPort(echoStkPort ? msg.getStkPort() : null);
        return m;
    }

    /** 封裝回覆 ACK（OK/FAIL） */
    private void replyAck(String targetSystem,
                          R008CommandPayload command,
                          R008AckPayload.Message ackMsg,
                          String result, String resultMessage) throws Exception {
        R008AckPayload ack = new R008AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R008");
        ack.setTid(command != null ? command.getTid() : null);
        ack.setIdDesc("ROBOT_MOVE_SCH_TO_WIP");
        ack.setMessage(ackMsg);
        ack.setResult(result);
        ack.setResultMessage(Objects.toString(resultMessage, ""));

        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(targetSystem, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /** 接受 G/B/E 或 GOOD/BAD/EMPTY */
    private static boolean isValidBinType(String v) {
        if (v == null) return false;
        String s = v.trim().toUpperCase();
        return "G".equals(s) || "B".equals(s) || "E".equals(s)
                || "GOOD".equals(s) || "BAD".equals(s) || "EMPTY".equals(s);
    }

    /** 正規化為 G/B/E（可用在建包或入庫流程，不在 validate 裡動資料） */
    private static String normalizeBinType(String v) {
        if (v == null) return null;
        String s = v.trim().toUpperCase();
        return switch (s) {
            case "G", "GOOD"  -> "G";
            case "B", "BAD"   -> "B";
            case "E", "EMPTY" -> "E";
            default -> null;
        };
    }

    /** MOVE_PRIORITY 整數字串 → 佇列 priority（1 高→9 低）。預設 5。 */
    private static int mapMovePriorityToInboxPriority(Integer mp) {
        // Integer i = parseInt(mp);
        Integer i = mp;
        if (i == null) return 5;
        return Math.max(1, Math.min(9, 10 - i)); // 越大越高 → 越接近 1
    }

    // ---- helpers ----
    private static String toText(Object o) {
        if (o == null) return null;
        String s = o.toString();
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer parseInt(String s) {
        try {
            return (s == null) ? null : Integer.parseInt(s.trim());
        } catch (Exception ignore) { return null; }
    }

    private static BigDecimal parseDecimal(String s) {
        try {
            return (s == null) ? null : new BigDecimal(s.trim());
        } catch (Exception ignore) { return null; }
    }

    private static BigDecimal toDecimal(String s) {
        BigDecimal d = parseDecimal(s);
        return (d == null) ? null : d.setScale(Math.min(d.scale(), 3), BigDecimal.ROUND_HALF_UP);
    }

    private static Integer toInteger(String s) {
        return parseInt(s);
    }

    private static String safeMsg(Throwable t) {
        String m = (t != null) ? t.getMessage() : null;
        return (m == null || m.isBlank()) ? (t != null ? t.getClass().getSimpleName() : "UnknownError") : m;
    }

    private static boolean safeEqIgnoreCase(String a, String b) {
        a = toText(a); b = toText(b);
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }
}
