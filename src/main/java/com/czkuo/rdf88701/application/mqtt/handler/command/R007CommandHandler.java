package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R007AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.R007CommandPayload;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQueryPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQuerySecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import com.czkuo.rdf88701.domain.repository.MqttInboxRepository;
import com.czkuo.rdf88701.domain.repository.RobotInR007Repository;
import com.czkuo.rdf88701.domain.repository.RobotR007TaskRepository;
import com.czkuo.rdf88701.infra.entity.RobotInR007;
import com.czkuo.rdf88701.infra.entity.RobotR007Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * R007CommandHandler
 * - 負責處理 CMD_ID=R007 的指令（WIP(STK) 搬貨至機台）
 * - 支援 SAA→SEEC 及 ASE→廠商兩種模式，根據場景 MESSAGE 欄位有不同組包規則
 * - 處理流程：
 *   0) 參數驗證 + 標準化（避免空字串/空白造成 DB 例外）
 *   1) 記錄 COMMAND 訊息至 mqtt_message_log（不論收/拒都落地）
 *   2) 向 ZIP 查 Type=2（Name=CARRIERID）：僅當 Status=33(上架) 才接單
 *   3) 接單：落地 robot_in_r007、寫入隊列（mqtt_inbox）、upsert robot_r007_task
 *   4) 回覆 ACK（OK/FAIL），任何未預期例外都會回 FAIL + 原因
 */
@Slf4j
@Component
public class R007CommandHandler extends AbstractCommandHandler<R007CommandPayload> {

    private final MqttMessageLogService logService;           // 寫 mqtt_message_log（須支援回傳 id）
    private final SystemContext systemContext;                // 本系統代碼
    private final RobotInR007Repository r007Repository;       // R007 MESSAGE 明細
    private final RobotR007TaskRepository taskRepository;     // R007 任務明細（以 log_id 冪等）
    private final MqttInboxRepository inboxRepository;        // 入站佇列
    private final ZipStockerCommandService zipCommandService; // ZIP 查詢服務

    public R007CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              RobotInR007Repository r007Repository,
                              RobotR007TaskRepository taskRepository,
                              MqttInboxRepository inboxRepository,
                              ZipStockerCommandService zipCommandService) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.r007Repository = r007Repository;
        this.taskRepository = taskRepository;
        this.inboxRepository = inboxRepository;
        this.zipCommandService = zipCommandService;
    }

    /**
     * 處理收到的 R007 指令
     *
     * @param system  來源系統（如 SAA/ASE）
     * @param topic   MQTT topic
     * @param command 已反序列化的 R007CommandPayload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, R007CommandPayload command, MqttMessageType type) throws Exception {
        // ===== 0) 參數驗證 + 標準化 =====
        final R007CommandPayload.Message rawMsg = command.getMessage();
        final R007CommandPayload.Message msg = normalizeMessage(rawMsg); // 將空白→null、trim 文字欄位

        List<String> errors = validate(command, msg);
        if (!errors.isEmpty()) {
            // 還未落 log 時無 logId，但仍應回 ACK=FAIL
            replyAck(system, command, echoAckMessage(msg, /*echoStkPort*/ false), "FAIL", String.join("; ", errors));
            log.warn("[R007] 參數驗證失敗：tid={}, errors={}", command.getTid(), errors);
            return;
        }

        final String tid       = command.getTid();
        final String carrierId = msg.getCarrierId();

        Long logId = null;
        try {
            // ===== 1) 不論收/拒都先記錄 COMMAND 至 mqtt_message_log（便於稽核） =====
            final JsonNode payload = objectMapper.valueToTree(command);
            logId = logService.recordReturningId(
                    topic,
                    system,                        // sender：對方
                    systemContext.getSystemCode(), // receiver：本系統
                    payload,
                    MqttMessageType.COMMAND
            );

            // ===== 2) ZIP Type=2 查詢（Name=CARRIERID）：需命中且 Status=33 才接單 =====
            String rejectReason = null;
            try {
                ZipStatusHit hit = queryType2FromZipa(carrierId);
                if (!hit.found) {
                    rejectReason = "ZIP 未找到產品（Type=2, Name=" + carrierId + "）";
                } else if (hit.status != 33) {
                    // 狀態定義：31入倉搬運中/32出倉搬運中/33上架/34出倉/35手動下架/38入倉輸送中/39出庫輸送中/104不在倉儲
                    rejectReason = "ZIP 狀態不允許接單（目前=" + hit.status + "，需=33 上架）";
                } else {
                    log.info("[R007] ZIP 命中：carrierId={} status=33(上架) 來源={}", carrierId, hit.source);
                }
            } catch (Exception e) {
                log.error("[R007] ZIP 狀態查詢失敗：tid={}, carrierId={}, err={}", tid, carrierId, e.getMessage(), e);
                rejectReason = "ZIP 狀態查詢失敗：" + e.getMessage();
            }

            if (rejectReason != null) {
                // 3a) 拒絕：不落地 robot_in_r007、不入佇列；直接回 FAIL
                replyAck(system, command, echoAckMessage(msg, false), "FAIL", rejectReason);
                log.warn("[R007] 拒絕接單：tid={}, reason={}", tid, rejectReason);
                return;
            }

            // ===== 3') 重複任務檢查（以 TID 或 關鍵欄位組合 判斷；僅針對未結案的任務）=====
            RobotR007Task dup = findOngoingDuplicate(msg, tid);
            if (dup != null) {
                log.warn("[R007] 偵測到重複任務：tid={} carrierId={} destLoc={} eqpPort={} 已存在任務 id={}, internal_state={}",
                        tid, msg.getCarrierId(), msg.getDestLoc(), msg.getEqpPort(), dup.getId(), dup.getInternalState());
                // 可選追蹤：標記外部互動
                // try {
                //     dup.setExternalLastResult("DUPLICATE");
                //     dup.setExternalLastTime(LocalDateTime.now());
                //     taskRepository.update(dup);
                // } catch (Exception ignore) {
                //     //log.debug("[R007] 更新既有任務 external_last_* 失敗（可忽略） tid={}", tid);
                // }
                // 冪等回覆：FAIL，但不重複落任務/不入佇列
                replyAck(system, command, echoAckMessage(msg, /*echoStkPort*/ false),
                        "FAIL", "DUPLICATE: task already exists (tid=" + dup.getTid() + ")");
                return;
            }

            // ===== 3b) 接單：落地 MESSAGE 明細 robot_in_r007（以 log_id 為 PK/或 unique） =====
            try {
                RobotInR007 row = new RobotInR007();
                row.setLogId(logId);
                row.setLotId(msg.getLotId());
                row.setCarrierId(msg.getCarrierId());
                row.setWipName(msg.getWipName());
                row.setDestLoc(msg.getDestLoc());
                row.setEqpPort(msg.getEqpPort());
                row.setDeviceName(msg.getDeviceName()); // 允許 null/空字串
                row.setStkPort(msg.getStkPort());

                if (r007Repository.findById(logId).isPresent()) { // 若你的 robot_in_r007 以 log_id 為 PK
                    r007Repository.update(row);
                } else {
                    r007Repository.save(row);
                }
            } catch (DataAccessException dae) {
                // DB 例外：回 ACK=FAIL + 原因
                String reason = "DB 錯誤：寫入 robot_in_r007 失敗 - " + safeMsg(dae);
                replyAck(system, command, echoAckMessage(msg, false), "FAIL", reason);
                log.error("[R007] {}", reason, dae);
                return;
            }

            // ===== 4) upsert robot_r007_task（以 uq: log_id）=====
            try {
                RobotR007Task task = new RobotR007Task();
                task.setLogId(logId);
                task.setInboxId(null);
                task.setTid(tid);

                // 核心欄位
                task.setLotId(msg.getLotId());
                task.setCarrierId(msg.getCarrierId());
                task.setWipName(msg.getWipName());
                task.setDestLoc(msg.getDestLoc());
                task.setEqpPort(msg.getEqpPort());
                task.setDeviceName(msg.getDeviceName());

                // ZIP 出料 Port 由 Worker 決策，接單時先不填
                task.setStkPort(null);

                // 其他 MESSAGE 欄位
                task.setTrayHigh(msg.getTrayHigh());
                task.setTrayType(msg.getTrayType());
                task.setTrayNum(msg.getTrayNum());
                task.setMovePriority(msg.getMovePriority());
                task.setMissionTrip(msg.getMissionTrip());
                task.setOdo(msg.getOdo());
                task.setAmrSpeed(msg.getAmrSpeed());
                task.setAmrRobotSpeed(msg.getAmrRobotSpeed());
                task.setPpkgBodySize(msg.getPpkgBodySize());
                task.setFlip(msg.getFlip()); // 協定鍵 FLIP → entity 欄位 flip
                try {
                    task.setRawMessageJson(objectMapper.writeValueAsString(msg));
                } catch (Exception e) {
                    log.warn("[R007] rawMessageJson 序列化失敗：{}", e.getMessage());
                    task.setRawMessageJson(null);
                }

                // 預設需求/狀態（之後由 Walker/ACK Handler 更新）
                task.setZipRequired(Boolean.TRUE);
                task.setAmrRequired(Boolean.TRUE);
                task.setZipState("PENDING");
                task.setZipAttempts(0);
                task.setAmrState("PENDING");
                task.setAmrAttempts(0);

                // 內外狀態：接單成功 → 佇列中
                task.setInternalState("QUEUED");
                task.setExternalLastResult("OK");
                task.setExternalLastTime(LocalDateTime.now());
                task.setCreatedTime(LocalDateTime.now());
                task.setUpdatedTime(LocalDateTime.now());

                // 冪等：以 uq(log_id) 為準（不要用 PK id）
                if (taskRepository.findByLogId(logId).isPresent()) {
                    taskRepository.updateByLogId(task);
                } else {
                    taskRepository.save(task);
                }
            } catch (DataAccessException dae) {
                String reason = "DB 錯誤：寫入 robot_r007_task 失敗 - " + safeMsg(dae);
                replyAck(system, command, echoAckMessage(msg, false), "FAIL", reason);
                log.error("[R007] {}", reason, dae);
                return;
            }

            // ===== 5) 入佇列（RECEIVED→我們用 QUEUED 對應資料表）→ 取得 inboxId =====
            Long inboxId;
            try {
                inboxId = inboxRepository.enqueueFromInbound(
                        logId,
                        tid,
                        command.getCmdId(),
                        system,                        // sender
                        systemContext.getSystemCode(), // receiver
                        topic,
                        LocalDateTime.now(),
                        5 // priority（1 高 → 9 低）
                );
            } catch (DataAccessException dae) {
                String reason = "DB 錯誤：寫入 mqtt_inbox 失敗 - " + safeMsg(dae);
                replyAck(system, command, echoAckMessage(msg, false), "FAIL", reason);
                log.error("[R007] {}", reason, dae);
                return;
            }

            // ===== 6) 回填 task.inbox_id
            taskRepository.updateInboxIdByLogId(logId, inboxId);

            // ===== 7) 回 ACK=OK =====
            replyAck(system, command, echoAckMessage(msg, false), "OK", "");
            log.info("[R007] 成功：logId={}，已入佇列並建立/更新任務（internal_state=QUEUED）", logId);

        } catch (Exception ex) {
            // ===== 全域未預期例外：務必回 ACK=FAIL =====
            String reason = "未預期錯誤：" + safeMsg(ex);
            try {
                replyAck(system, command, echoAckMessage(command.getMessage(), false), "FAIL", reason);
            } catch (Exception ackEx) {
                log.error("[R007] 發送失敗 ACK 亦發生錯誤：{}", ackEx.getMessage(), ackEx);
            }
            log.error("[R007] 致命錯誤：tid={}, logId={}, err={}", command.getTid(), logId, ex.getMessage(), ex);
        }
    }

    /** 回傳對應的 CMD_ID，供 Router 註冊與分派 */
    @Override
    protected String getCmdIdInternal() {
        return "R007";
    }

    /** 回傳 payload 型別，供 Jackson 反序列化 */
    @Override
    protected Class<R007CommandPayload> getCommandType() {
        return R007CommandPayload.class;
    }

    // ===================== 私有方法 =====================

    /** 以目前約定 echo 回去的 MESSAGE（對 ASE 通常不回 STK_PORT） */
    private R007AckPayload.Message echoAckMessage(R007CommandPayload.Message msg, boolean echoStkPort) {
        R007AckPayload.Message m = new R007AckPayload.Message();
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
                          R007CommandPayload command,
                          R007AckPayload.Message ackMsg,
                          String result, String resultMessage) throws Exception {

        R007AckPayload ack = new R007AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R007");
        ack.setTid(command.getTid());
        ack.setIdDesc("ROBOT_MOVE_SCH_TO_EQP");
        ack.setMessage(ackMsg);
        ack.setResult(result);
        ack.setResultMessage(Objects.toString(resultMessage, ""));

        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(targetSystem, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /** 僅向 ZIPA 查 Type=2：命中則回傳狀態；未命中則 found=false */
    private ZipStatusHit queryType2FromZipa(String carrierId) {
        StatusQueryPrimaryBody.QueryInfo qi = new StatusQueryPrimaryBody.QueryInfo();
        qi.setType(2);
        qi.setName(carrierId);

        ZipTarget t = ZipTarget.ZIPA;
        try {
            Root<StatusQuerySecondaryBody> resp = zipCommandService.sendStatusQuery(t, qi);
            Integer status = pickType2Status(resp, carrierId);
            if (status != null) {
                return new ZipStatusHit(true, status, t.name());
            }
        } catch (Exception e) {
            log.warn("[R007] 查詢 {} Type=2 失敗，carrierId={}：{}", t, carrierId, e.getMessage());
        }
        return new ZipStatusHit(false, -1, null);
    }

    /** 從 ZIP 回覆挑出 carrierId 對應的 Type=2 狀態碼（31/32/33/34/35/38/39） */
    private Integer pickType2Status(Root<StatusQuerySecondaryBody> resp, String carrierId) {
        if (resp == null || resp.getBody() == null || resp.getBody().getStatusInfos() == null) return null;
        List<StatusQuerySecondaryBody.StatusInfo> list = resp.getBody().getStatusInfos();
        for (StatusQuerySecondaryBody.StatusInfo s : list) {
            if (s == null) continue;
            Integer type = toInt(s.getType());
            if (type == null || type != 2) continue;

            String name = toText(s.getName());
            if (name != null && name.equalsIgnoreCase(carrierId)) {
                return toInt(s.getStatus());
            }
        }
        return null;
    }

    /** 小型回傳模型：命中與否 + 狀態碼 + 來源 ZIP */
    private record ZipStatusHit(boolean found, int status, String source) {}

    // ---- helpers ----
    private static String toText(Object o) {
        if (o == null) return null;
        String s = o.toString();
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String safeMsg(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }

    /**
     * 將文字欄位做基本標準化（trim；全空白→null）
     * - 特別注意 deviceName：允許空字串或 null（DB 欄位請改為 DEFAULT NULL）
     */
    private static R007CommandPayload.Message normalizeMessage(R007CommandPayload.Message src) {
        if (src == null) return null;
        R007CommandPayload.Message m = new R007CommandPayload.Message();

        m.setLotId(toText(src.getLotId()));
        m.setCarrierId(toText(src.getCarrierId()));
        m.setWipName(toText(src.getWipName()));
        m.setDestLoc(toText(src.getDestLoc()));
        m.setEqpPort(toText(src.getEqpPort()));

        // deviceName 允許 "" 或 null：這裡僅做 trim，不強制轉 null
        String dn = src.getDeviceName();
        m.setDeviceName(dn == null ? null : dn.trim());

        // 其他欄位
        m.setTrayHigh(src.getTrayHigh());                // BigDecimal：保持原值，入庫前再 setScale
        m.setTrayType(toText(src.getTrayType()));        // "" -> null
        m.setTrayNum(src.getTrayNum());                  // Integer：保持

        m.setMovePriority(src.getMovePriority());        // Integer：驗證時檢查範圍
        m.setMissionTrip(toText(src.getMissionTrip()));
        m.setOdo(src.getOdo());
        m.setAmrSpeed(src.getAmrSpeed());
        m.setAmrRobotSpeed(src.getAmrRobotSpeed());
        m.setPpkgBodySize(toText(src.getPpkgBodySize())); // "" -> null

        // FILP：轉成大寫；空白->null
        String flip = toText(src.getFlip());
        m.setFlip(flip == null ? null : flip.toUpperCase());

        m.setStkPort(toText(src.getStkPort()));

        return m;
    }

    /**
     * 驗證必要欄位；有錯回傳錯誤清單
     * - 必填：LOT_ID, CARRIERID, WIPNAME, DEST_LOC, EQP_PORT
     * - 必填：TRAY_TYPE(料號), TRAY_HIGH, TRAY_NUM
     * - 規則：TRAY_TYPE 長度 ≤ 64；TRAY_HIGH >= 0 且小數位 ≤ 3；TRAY_NUM 為整數且 >= 0
     */
    private static List<String> validate(R007CommandPayload cmd, R007CommandPayload.Message msg) {
        List<String> errs = new ArrayList<>();
        if (cmd == null) { errs.add("payload 為空"); return errs; }
        if (toText(cmd.getTid()) == null) errs.add("TID 缺失");
        if (msg == null) { errs.add("MESSAGE 缺失"); return errs; }

        if (toText(msg.getLotId()) == null)    errs.add("LOT_ID 缺失");
        if (toText(msg.getCarrierId()) == null)errs.add("CARRIERID 缺失");
        if (toText(msg.getWipName()) == null)  errs.add("WIPNAME 缺失");
        if (toText(msg.getDestLoc()) == null)  errs.add("DEST_LOC 缺失");
        if (toText(msg.getEqpPort()) == null)  errs.add("EQP_PORT 缺失");
        // deviceName 可為空或 null，不驗證

        // TRAY_TYPE（料號）：必填；長度 ≤ 64
        String trayType = msg.getTrayType();
        if (trayType == null) {
            errs.add("TRAY_TYPE 缺失");
        } else if (trayType.length() > 64) {
            errs.add("TRAY_TYPE 長度超過 64");
        }

        // TRAY_HIGH：必填；>=0 且小數位 ≤ 3
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

        // TRAY_NUM：必填；整數且 >=0
        if (msg.getTrayNum() == null) {
            errs.add("TRAY_NUM 缺失");
        } else {
            try {
                int n = msg.getTrayNum();
                if (n < 0) errs.add("TRAY_NUM 不可為負數");
            } catch (Exception e) {
                errs.add("TRAY_NUM 非法整數");
            }
        }

        // ====== MOVE_PRIORITY ======
        if (msg.getMovePriority() != null) {
            int p = msg.getMovePriority();
            if (p < 0 || p > 9) {
                errs.add("MOVE_PRIORITY 僅允許 0~9");
            }
        }

        // ====== PPKG_BODY_SIZE（可選；長度 ≤32；字元白名單）======
        // if (msg.getPpkgBodySize() != null) {
        //     String s = msg.getPpkgBodySize();
        //     if (s.length() > 32) {
        //         errs.add("PPKG_BODY_SIZE 長度超過 32");
        //     } else if (!s.matches("^[A-Za-z0-9._xX\\-]+$")) {
        //         errs.add("PPKG_BODY_SIZE 僅允許英數與 . _ - x/X");
        //     }
        // }

        // ====== FLIP（只能 Y/N）======
        if (msg.getFlip() == null) {
            errs.add("FLIP 缺失");
        }
        else {
            String f = msg.getFlip();
            if (!("Y".equals(f) || "N".equals(f))) {
                errs.add("FLIP 僅允許 'Y' 或 'N'");
            }
        }

        return errs;
    }

    /** 判斷是否有「進行中」且重複的 R007 任務：
     *  - 冪等條件1：相同 TID（合作方重送/重試）
     *  - 冪等條件2：相同關鍵組合（CARRIERID + DEST_LOC + EQP_PORT），且仍未結案
     *  - 僅針對 internal_state ∈ {QUEUED, PROCESSING} 視為進行中
     */
    private RobotR007Task findOngoingDuplicate(R007CommandPayload.Message msg, String tid) {

        String inCarrier = toText(msg.getCarrierId());
        String inDest    = toText(msg.getDestLoc());
        String inEqp     = toText(msg.getEqpPort());
        String inTid     = toText(tid);

        // 最小入侵：findAll() 掃描（如需效能可改 repo 提供 findOpen() / 專用 SQL）
        List<RobotR007Task> all = taskRepository.findOpen();
        for (RobotR007Task t : all) {

            // 條件1：同 TID（最強冪等條件）
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

    private static boolean safeEqIgnoreCase(String a, String b) {
        a = toText(a); b = toText(b);
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }
}
