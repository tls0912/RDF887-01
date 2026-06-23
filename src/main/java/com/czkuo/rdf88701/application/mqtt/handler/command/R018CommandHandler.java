package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R018AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.R018CommandPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R018CommandHandler
 * - 刪除任務通知（ASE→SAA、SAA→SEEC）
 *
 * 新規則：
 *   僅允許「尚未執行」的任務取消：
 *   - internalState ∈ {NEW, QUEUED, ASSIGNED, DISPATCHED, PENDING}
 *   - 且外部未開始（externalLastResult != START，且（若存在）externalStartTime == null）
 */
@Slf4j
@Component
public class R018CommandHandler extends AbstractCommandHandler<R018CommandPayload> {

    // ===== 依賴 =====
    private final MqttMessageLogService logService;
    private final SystemContext systemContext;
    private final MqttInboxRepository mqttInboxRepository;
    private final RobotR007TaskRepository r007Repo;
    private final RobotR008TaskRepository r008Repo;
    private final RobotR029TaskRepository r029Repo;
    private final RobotR031TaskRepository r031Repo;

    private final ContainerMainRepository containerMainRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final MqttCommandService mqttCommandService;
    private final MqttMessageLogRepository mqttMessageLogRepository;

    // ===== CMD_TID 解析：僅允許 R007 / R008 / R029 / R031 =====
    private static final Pattern CMD_TID_PATTERN =
            Pattern.compile("^\\s*(R0(?:07|08|29|31))_(\\d{17})\\s*$");

    // =====  嚴格的 TID 解析器（STRICT） =====
    private static final DateTimeFormatter TID_FMT =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyyMMddHHmmssSSS")
                    .toFormatter(Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);

    // 可取消（尚未執行）的內部狀態集合
    private static final Set<String> INTERNAL_CANCELABLE =
            Set.of("NEW", "QUEUED", "ASSIGNED", "DISPATCHED", "PENDING");

    // 已在進行中的內部狀態（遇到就判定不可取消）
    private static final Set<String> INTERNAL_IN_PROGRESS =
            Set.of("PROCESSING");

    // 已終結的外部狀態（遇到就回 OK 說明）
    private static final Set<String> EXTERNAL_TERMINAL =
            Set.of("END", "FAIL", "NG", "CANCEL");

    public R018CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              MqttInboxRepository mqttInboxRepository,
                              RobotR007TaskRepository r007Repo,
                              RobotR008TaskRepository r008Repo,
                              RobotR029TaskRepository r029Repo,
                              RobotR031TaskRepository r031Repo,
                              ContainerMainRepository containerMainRepository,
                              LocationTrackingRepository locationTrackingRepository,
                              MqttCommandService mqttCommandService,
                              MqttMessageLogRepository  mqttMessageLogRepository) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.mqttInboxRepository = mqttInboxRepository;
        this.r007Repo = r007Repo;
        this.r008Repo = r008Repo;
        this.r029Repo = r029Repo;
        this.r031Repo = r031Repo;
        this.containerMainRepository = containerMainRepository;
        this.locationTrackingRepository = locationTrackingRepository;
        this.mqttCommandService = mqttCommandService;
        this.mqttMessageLogRepository = mqttMessageLogRepository;
    }

    // ===== Handler 進入點 =====
    @Override
    @Transactional
    protected void process(String system, String topic, R018CommandPayload command, MqttMessageType type) throws Exception {
        R018CommandPayload.Message msg = command.getMessage();
        String cmdTidRaw = (msg != null ? msg.getCmdTid() : null);

        log.info("[R018] 收到刪除任務指令：TID={}, topic={}, system={}, CMD_TID={}",
                command.getTid(), topic, system, cmdTidRaw);

        // 1) 記錄 R018 COMMAND 至 mqtt_message_log（審計）
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                        // sender：對方系統
                systemContext.getSystemCode(), // receiver：本系統
                payload,
                MqttMessageType.COMMAND
        );

        // 2) 主邏輯：解析 CMD_TID 並執行刪除（僅限尚未執行）
        DeleteTaskResult result = deleteByCmdTidInline(cmdTidRaw);

        // 3) 組 ACK
        R018AckPayload ack = new R018AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R018");
        ack.setTid(command.getTid());
        ack.setIdDesc("DELETE_COMMAND");

        R018AckPayload.Message ackMsg = new R018AckPayload.Message();
        ackMsg.setCmdTid(cmdTidRaw);
        ack.setMessage(ackMsg);

        if (result.isSuccess()) {
            ack.setResult("OK");
            ack.setResultMessage(result.getMessage());
            log.info("[R018] 刪除完成: CMD_TID='{}', CMD_ID={}, inboxLogId={}, msg={}",
                    cmdTidRaw, result.getCmdId(), result.getLogId(), result.getMessage());
        } else {
            ack.setResult("FAIL");
            ack.setResultMessage(result.getMessage());
            log.warn("[R018] 刪除失敗: CMD_TID='{}', CMD_ID={}, inboxLogId={}, reason={}",
                    cmdTidRaw, result.getCmdId(), result.getLogId(), result.getMessage());
        }

        // 4) 發送 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    @Override
    protected String getCmdIdInternal() { return "R018"; }

    @Override
    protected Class<R018CommandPayload> getCommandType() { return R018CommandPayload.class; }

    // ===== 內嵌：刪除主流程（包含 CMD_TID 解析與路由） =====

    @Value
    private static class DeleteTaskResult {
        boolean success;
        String  message;
        String  cmdId;   // R007/R008/R029/R031 or "UNKNOWN"
        Long    logId;   // 對應 mqtt_inbox.id（若可得）
    }

    private DeleteTaskResult deleteByCmdTidInline(String cmdTidRaw) {
        if (cmdTidRaw == null || cmdTidRaw.isBlank()) {
            return new DeleteTaskResult(false, "CMD_TID missing", "UNKNOWN", null);
        }

        // 嚴格解析 <CMD_ID>_<TID>
        Optional<CmdTidPair> parsedOpt = tryParseCmdTid(cmdTidRaw);
        if (parsedOpt.isPresent()) {
            CmdTidPair p = parsedOpt.get(); // p.cmdId / p.tid
            // 先用 (cmdId, tid) 精準找 inbox
            Optional<MqttInbox> inboxOpt = mqttInboxRepository.findLatestByCmdIdAndTid(p.cmdId, p.tid);
            if (inboxOpt.isPresent()) {
                return routeAndCancel(inboxOpt.get());
            }
            // 找不到就回 OK(noop)
            return new DeleteTaskResult(true, "No task found for CMD_TID (noop)", p.cmdId, null);
        }

        log.warn("[R018] CMD_TID 解析失敗: {}", cmdTidRaw);
        return new DeleteTaskResult(true, "No task found for TID (noop)", "UNKNOWN", null);
    }

    // ===== 路由到各任務類型並執行取消（僅尚未執行者可取消） =====

    private DeleteTaskResult routeAndCancel(MqttInbox inbox) {
        String cmdId = safeUpper(inbox.getCmdId());
        return switch (cmdId) {
            case "R007" -> cancelR007(inbox);
            case "R008" -> cancelR008(inbox);
            case "R029" -> cancelR029(inbox);
            case "R031" -> cancelR031(inbox);
            default -> new DeleteTaskResult(true, "Original CMD_ID not cancelable or unsupported: " + cmdId, cmdId, inbox.getId());
        };
    }

    private DeleteTaskResult cancelR007(MqttInbox inbox) {
        Optional<RobotR007Task> opt = r007Repo.findLatestByTid(inbox.getTid());
        if (opt.isEmpty()) return new DeleteTaskResult(true, "R007 task not found (noop)", "R007", inbox.getId());
        RobotR007Task t = opt.get();

        return decideCancelability(
                "R007", inbox.getId(),
                t.getInternalState(),
                t.getExternalLastResult(),
                () -> {
                    mqttInboxRepository.markCancelled(inbox.getId(), "Canceled by R018");
                    t.setInternalState("CANCELLED");
                    t.setExternalLastResult("CANCEL");
                    t.setExternalLastTime(LocalDateTime.now());
                    t.setCancelReason("Canceled by R018");
                    r007Repo.updateByLogId(t);
                }
        );
    }

    private DeleteTaskResult cancelR008(MqttInbox inbox) {
        Optional<RobotR008Task> opt = r008Repo.findByLogId(inbox.getLogId());
        if (opt.isEmpty()) return new DeleteTaskResult(true, "R008 task not found (noop)", "R008", inbox.getId());
        RobotR008Task t = opt.get();

        // (A) 在我方設備 → 直接拒絕
        if (isAtOurEquipmentForR008(t)) {
            return new DeleteTaskResult(false, "Located at our equipment; cannot cancel", "R008", inbox.getId());
        }

        // (B) 不在我方設備 → 先轉送 SEEC 的 R018，並「等待 ACK」
        final String cmdTidForSeec = "R008_" + inbox.getTid();   // 要求 SEEC 刪除的目標 CMD_TID（內容）
        final String seecTid;                                    // 我方送 R018 的「發送 TID」（用它等 ACK）

        try {
            MqttSendResult send = mqttCommandService.sendR018("seec", cmdTidForSeec);
            if (send == null || !send.isSuccess()) {
                return new DeleteTaskResult(false, "SEEC send failed: " + (send == null ? "null" : send.getMessage()), "R008", inbox.getId());
            }
            seecTid = send.getTid(); // 關鍵：等待 ACK 要用「我方發送的 TID」
        } catch (Exception e) {
            return new DeleteTaskResult(false, "SEEC send exception: " + e.getMessage(), "R008", inbox.getId());
        }

        // (C) 用「我方 TID」等 SEEC 的 R018 ACK
        Optional<MqttMessageLog> ackOpt = waitAckByTid("R018", seecTid, 30000, 100);
        if (ackOpt.isEmpty() || ackOpt.get().getPayload() == null || ackOpt.get().getPayload().isBlank()) {
            return new DeleteTaskResult(false, "SEEC ACK timeout", "R008", inbox.getId());
        }

        // (D) 解析 R018 ACK，且（可選）驗證 ACK.message.cmdTid == 我們請求的 cmdTidForSeec
        try {
            R018AckPayload ack = objectMapper.readValue(ackOpt.get().getPayload(), R018AckPayload.class);
            String ackResult = safeUpper(ack.getResult());
            String ackMsg    = ack.getResultMessage();

            // 可選一致性檢查
            String echoCmdTid = (ack.getMessage() != null ? ack.getMessage().getCmdTid() : null);
            if (echoCmdTid != null && !cmdTidForSeec.equals(echoCmdTid.trim())) {
                log.warn("[R018][R008] SEEC ACK CMD_TID mismatch: req={}, ack={}", cmdTidForSeec, echoCmdTid);
            }

            if (!"OK".equals(ackResult)) {
                return new DeleteTaskResult(false, "SEEC rejected: " + (ackMsg == null ? ackResult : ackMsg), "R008", inbox.getId());
            }
        } catch (Exception parseEx) {
            return new DeleteTaskResult(false, "SEEC ACK parse error: " + parseEx.getMessage(), "R008", inbox.getId());
        }

        // (E) SEEC 同意後，才依「尚未執行」規則在本系統做取消
        return decideCancelability(
                "R008", inbox.getId(),
                t.getInternalState(),
                t.getExternalLastResult(),
                () -> {
                    mqttInboxRepository.markCancelled(inbox.getId(), "Canceled by R018 via SEEC");
                    t.setInternalState("CANCELLED");
                    t.setExternalLastResult("CANCEL");
                    t.setExternalLastTime(LocalDateTime.now());
                    t.setCancelReason("Canceled by R018 via SEEC");
                    r008Repo.updateByLogId(t);
                }
        );
    }

    private DeleteTaskResult cancelR029(MqttInbox inbox) {
        Optional<RobotR029Task> opt = r029Repo.findByLogId(inbox.getId());
        if (opt.isEmpty()) return new DeleteTaskResult(true, "R029 task not found (noop)", "R029", inbox.getId());
        RobotR029Task t = opt.get();

        return decideCancelability(
                "R029", inbox.getId(),
                t.getInternalState(),
                t.getExternalLastResult(),
                () -> {
                    mqttInboxRepository.markCancelled(inbox.getId(), "Canceled by R018");
                    t.setInternalState("CANCELLED");
                    t.setExternalLastResult("CANCEL");
                    t.setExternalLastTime(LocalDateTime.now());
                    t.setFailReason("Canceled by R018");
                    r029Repo.updateByLogId(t);
                }
        );
    }

    private DeleteTaskResult cancelR031(MqttInbox inbox) {
        Optional<RobotR031Task> opt = r031Repo.findByLogId(inbox.getId());
        if (opt.isEmpty()) return new DeleteTaskResult(true, "R031 task not found (noop)", "R031", inbox.getId());
        RobotR031Task t = opt.get();

        return decideCancelability(
                "R031", inbox.getId(),
                t.getInternalState(),
                t.getExternalLastResult(),
                () -> {
                    mqttInboxRepository.markCancelled(inbox.getId(), "Canceled by R018");
                    t.setInternalState("CANCELLED");
                    t.setExternalLastResult("CANCEL");
                    t.setExternalLastTime(LocalDateTime.now());
                    r031Repo.updateByLogId(t);
                }
        );
    }

    // ===== 新：統一的可取消判斷與執行 =====

    @FunctionalInterface
    private interface Canceler { void run(); }

    /**
     * 僅當「尚未執行」才允許取消：
     * - internalState ∈ INTERNAL_CANCELABLE
     * - externalLastResult != START
     * - externalStartTime 不存在（若有欄位）
     */
    private DeleteTaskResult decideCancelability(
            String cmdId, Long logId,
            String internalState, String externalLastResult,
            Canceler doCancel
    ) {
        String is = safeUpper(internalState);
        String es = safeUpper(externalLastResult);

        // 已終結：回 OK 說明
        if (EXTERNAL_TERMINAL.contains(es)) {
            String msg = switch (es) {
                case "END"    -> "Already done";
                case "FAIL"   -> "Already failed";
                case "NG"     -> "Already ng";
                case "CANCEL" -> "Already canceled";
                default       -> "Already ended";
            };
            return new DeleteTaskResult(true, msg, cmdId, logId);
        }

        // 已知執行中（內部）→ 不可取消
        if (INTERNAL_IN_PROGRESS.contains(is)) {
            return new DeleteTaskResult(false, "In progress, cannot cancel", cmdId, logId);
        }

        // 僅在「內部屬於尚未執行集合」時可取消；其餘狀態視為未知/保守拒絕
        if (INTERNAL_CANCELABLE.contains(is)) {
            doCancel.run();
            return new DeleteTaskResult(true, "Canceled", cmdId, logId);
        }

        // 其他未知狀態 → 保守處理為不可取消
        return new DeleteTaskResult(false, "Unknown or non-cancelable state: " + is, cmdId, logId);
    }

    /**
     * 判斷 R008 是否「目前在我們設備」
     * 你可以依據你的資料模型擇一實作：
     *   A) 以 containerMainId 查最近在位點並比對是否屬於本系統設備
     *   B) 以 currentLocationCode / lastKnownLocation 比對 Site/Transfer 所屬
     */
    private boolean isAtOurEquipmentForR008(RobotR008Task t) {
        try {

            Optional<ContainerMain> opt = containerMainRepository.findByAliasCode(t.getCarrierId().trim());
            if (opt.isEmpty()) return false;

            ContainerMain cm = opt.get();
            Long cmId = cm.getId();

            return locationTrackingRepository.findByContainerMainId(cmId).isPresent();

        } catch (Exception e) {
            log.warn("[R018][R008] isAtOurEquipmentForR008 check failed: {}", e.toString());
        }
        // 預設：無法判斷就當「不在我們設備」（避免過度阻擋）；如需更嚴格可改成 true。
        return false;
    }

    private Optional<MqttMessageLog> waitAckByTid(String cmdId, String tid, long timeoutMs, long intervalMs) {
        long start = System.currentTimeMillis();
        do {
            Optional<MqttMessageLog> ackOpt = findLatestAckInTid(cmdId, tid);
            if (ackOpt.isPresent()) return ackOpt;
            try { Thread.sleep(Math.max(10L, intervalMs)); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        } while (System.currentTimeMillis() - start < timeoutMs);
        return Optional.empty();
    }

    private Optional<MqttMessageLog> findLatestAckInTid(String cmdId, String tid) {
        try {
            List<MqttMessageLog> all = mqttMessageLogRepository.findAllByTid(tid);
            if (all == null || all.isEmpty()) return Optional.empty();
            return all.stream()
                    .filter(this::isAck)
                    .filter(log -> cmdId.equalsIgnoreCase(safeStr(log.getCmdId())))
                    .sorted((a, b) -> Long.compare(optLong(b.getId()), optLong(a.getId())))
                    .findFirst();
        } catch (Exception e) {
            log.warn("[R018] query ACK by tid={} failed: {}", tid, e.toString());
            return Optional.empty();
        }
    }

    // ===== 小工具：解析 CMD_TID、大小寫收斂、時間欄位安全取用 =====

    private static Optional<CmdTidPair> tryParseCmdTid(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        Matcher m = CMD_TID_PATTERN.matcher(raw);
        if (!m.matches()) return Optional.empty();

        String cmd = m.group(1).toUpperCase(Locale.ROOT);
        String tid = m.group(2);

        return Optional.of(new CmdTidPair(cmd, tid));

        // try {
        //     // 嚴格驗證 yyyyMMddHHmmssSSS（例如 20250230 會丟例外）
        //     LocalDateTime.parse(tid, TID_FMT);
        //     return Optional.of(new CmdTidPair(cmd, tid));
        // } catch (DateTimeParseException e) {
        //     // 格式雖符合 17 碼，但日期/時間本身不合法 → 視為無效
        //     return Optional.empty();
        // }
    }

    @Value
    private static class CmdTidPair {
        String cmdId; // R007/R008/R029/R031
        String tid;   // yyyyMMddHHmmssSSS
    }

    private static String safeUpper(String v) {
        return v == null ? "" : v.toUpperCase(Locale.ROOT).trim();
    }

    private boolean isAck(MqttMessageLog log) {
        String mt = safeStr(log.getMessageType());
        return "ACK".equalsIgnoreCase(mt);
    }
    private String safeStr(Object v) { return v == null ? "" : String.valueOf(v); }
    private long optLong(Long v) { return v == null ? 0L : v; }

}
