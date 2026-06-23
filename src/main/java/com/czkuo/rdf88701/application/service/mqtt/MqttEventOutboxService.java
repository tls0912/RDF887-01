package com.czkuo.rdf88701.application.service.mqtt;

import com.czkuo.rdf88701.application.mqtt.util.MqttPayloadSanitizer;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.common.enums.MqttEventStatus;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.config.mqtt.MqttConfigProperties;
import com.czkuo.rdf88701.domain.repository.MqttEventLogRepository;
import com.czkuo.rdf88701.domain.repository.MqttEventStatusLogRepository;
import com.czkuo.rdf88701.infra.entity.MqttEventLog;
import com.czkuo.rdf88701.infra.entity.MqttEventStatusLog;
import com.czkuo.rdf88701.infra.mqtt.MqttClientManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


/**
 * Outbox 可靠推送服務（改良版：API 不阻塞，afterCommit 非同步啟動一次嘗試）
 *
 * 功能：
 *  1) enqueueAndTrySend：事件入箱，並在「交易提交後」啟動一次非同步嘗試（避免卡住 HTTP 執行緒）。
 *  2) trySendOnce：排程/入箱觸發的一次發送嘗試；依結果轉移狀態與排程。
 *  3) markAcked：收到對應 TID 的 ACK 時結案（僅 SENT/RETRYING → ACKED）。
 *
 * 設計：
 *  - 狀態字串以 enum.name() 寫入 DB（避免魔法字串）。
 *  - 退避：exponential backoff（起始/乘數/上限可由 YAML 調整）。
 *  - requireConnected=true：未連線時「不送、不計次」，僅重排程等待下次嘗試。
 *  - 不在 Outbox 任一路徑主動送 S001；連線由心跳/外部流程維持。
 *  - 並發：Repository 端提供 CAS（fromStatus 比對）避免多實例重送同一筆。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttEventOutboxService {

    private final MqttEventLogRepository eventRepo;
    private final MqttEventStatusLogRepository statusRepo;
    private final MqttDirectMessageSender messageSender;
    private final MqttConnectionService connectionService;
    private final MqttConfigProperties mqttProps;
    private final MqttClientManager mqttClientManager;

    /* 用於清洗/序列化 */
    private final MqttPayloadSanitizer payloadSanitizer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /* 可由 YAML 覆寫 */
    @Value("${mqtt.outbox.enabled:true}")
    private boolean enabled;

    @Value("${mqtt.outbox.max-attempts:10}")
    private int maxAttempts;

    @Value("${mqtt.outbox.initial-backoff-seconds:2}")
    private int initialBackoffSec;

    @Value("${mqtt.outbox.multiplier:2.0}")
    private double backoffMultiplier;

    @Value("${mqtt.outbox.max-backoff-seconds:60}")
    private int maxBackoffSec;

    @Value("${mqtt.command.require-connected:true}")
    private boolean requireConnected;

    @Value("${mqtt.outbox.discard-older-than-seconds:86400}")
    private int discardOlderThanSec;

    @Value("${mqtt.outbox.ack-timeout-seconds:30}")
    private int ackTimeoutSec;

    /**
     * 非同步執行器（最小依賴版本）。
     * 若你已有全域 TaskExecutor，建議以建構子注入改為該 Executor，避免額外的執行緒池。
     */
    private final Executor mqttOutboxExecutor;

    /**
     * 入箱 + 交易提交後啟動一次非同步嘗試。
     *
     * 規則：
     *  - requireConnected 且未連線：僅入箱並重排程，★ 不送 S001；API 立即回應。
     *  - 其餘情況：在「交易提交後」非同步 trySendOnce（避免卡住當前 HTTP 執行緒）。
     *
     * 回傳：
     *  - 一律回 success(tid)（代表「已入箱，後續由 Outbox 機制處理」）；實際是否已送出請以事件狀態查表為準。
     */
    @Transactional
    public MqttSendResult enqueueAndTrySend(
            String cmdId,        // event_type（如 S007/R007）
            String targetSystem, // seec / ase
            String payloadJson,  // 原始 JSON
            String tid,          // 既有 TID（唯一）
            boolean requireAck   // 是否需要等待 ACK
    ) {
        if (!enabled) {
            log.warn("[OUTBOX] disabled; event is enqueued, background sending relies on worker.");
        }

        String sys = normalize(targetSystem);
        var conn = mqttProps.getConnections().get(sys);
        if (conn == null) {
            log.error("[OUTBOX][{}] unknown target system '{}'", tid, sys);
            return MqttSendResult.fail("Unknown target system: " + sys, tid);
        }

        String topic = conn.getSendTopic();
        LocalDateTime now = LocalDateTime.now();

        // === 1) 審計用 payload：清洗後存 DB ===
        String sanitizedPayload = safelySanitize(payloadJson);

        // 建立 Outbox 事件
        MqttEventLog ev = new MqttEventLog();
        ev.setEventType(cmdId);
        ev.setTid(tid);
        ev.setTopic(topic);
        ev.setTargetSystem(sys);
        ev.setRequireAck(requireAck);
        ev.setStatus(MqttEventStatus.PENDING.name());
        ev.setEventTime(now);
        ev.setSendTime(null);
        ev.setAckTime(null);
        ev.setRetryCount(0);
        ev.setNextAttemptTime(now);      // 預設立即可嘗試
        ev.setPayload(sanitizedPayload); // DB 僅存清洗版
        ev.setResultMessage(null);
        ev.setCreatedTime(now);
        ev.setUpdatedTime(now);

        eventRepo.save(ev);

        // 若極少數沒自增回填，用 TID 兜底
        if (ev.getId() == null) {
            eventRepo.findByTid(tid).ifPresent(found -> ev.setId(found.getId()));
        }

        // 狀態歷程（需要 eventId）
        if (ev.getId() != null) {
            logTransition(ev.getId(), null, ev.getStatus(), "system", "enqueue");
        }

        // 未連線：不送、不計次、也不送 S001；只重排程，等待下次嘗試（排程或 worker）
        if (requireConnected && !isClientConnected(sys)) {
            int backoff = calcBackoffSec(currentAttemptIndex(ev));
            eventRepo.updateNextAttemptTime(ev.getId(), now.plusSeconds(backoff));
            log.info("[OUTBOX][{}] queued (NOT CONNECTED), sys={}, nextAttempt+{}s", tid, sys, backoff);
            return MqttSendResult.success(tid);
        }

        // 關鍵：不要在交易內與 HTTP 執行緒內直接送；改為 afterCommit 非同步啟動一次
        // === 2) afterCommit：第一次送出要用「原始 payloadJson」 ===
        final String firstSendRawPayload = payloadJson;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        trySendOnce(ev, firstSendRawPayload); // 用原文送出
                    } catch (Exception ex) {
                        log.warn("[OUTBOX][{}] afterCommit error: {}", tid, ex.toString(), ex);
                    }
                }, mqttOutboxExecutor);
            }
        });

        // API 立即回應，實際送出由 afterCommit 的非同步任務處理
        return MqttSendResult.success(tid);
    }

    /**
     * 單次發送嘗試（給 worker / afterCommit 觸發的一次非同步嘗試）。
     *
     * 規則：
     *  - 已結案(ACKED/FAILED) → 跳過
     *  - 達上限 → FAILED（CAS）
     *  - ★ 未連線 → 不送、不計次，只重排程（不送 S001）
     *  - ★ 過舊（事件時間超過閥值）→ 直接 FAILED（不再嘗試）
     *  - 發送成功：需 ACK → SENT（安排超時補償）；不需 ACK → SENT → 立即 ACKED
     *  - 發送失敗/例外 → RETRYING（+retryCount）+ 重排程
     *
     * 注意：
     *  - 這裡應在非交易上下文執行（或自行決定），避免與 API 交易產生鎖衝突。
     */
    public void trySendOnce(MqttEventLog ev) {
        trySendOnce(ev, null);
    }

    public void trySendOnce(MqttEventLog ev, String overridePayloadForThisAttempt) {
        // 提前宣告，供過舊判斷與後續共用
        final LocalDateTime now = LocalDateTime.now();
        String cur = ev.getStatus();

        // 已結案
        if (statusEq(cur, MqttEventStatus.ACKED) || statusEq(cur, MqttEventStatus.FAILED)) return;

        // 過舊閥門 — 事件時間（或建立時間）距今超過 discardOlderThanSec 就不送
        if (isExpired(ev, now)) {
            String reason = "expired: older than " + discardOlderThanSec + "s";
            if (eventRepo.tryMarkFailed(ev.getId(), cur, reason)) {
                logTransition(ev.getId(), cur, MqttEventStatus.FAILED.name(), "system", "expired");
                log.info("[OUTBOX][{}] skip (expired), type={}, sys={}", ev.getTid(), ev.getEventType(), ev.getTargetSystem());
            }
            return;
        }

        // 達上限 → FAILED
        if (ev.getRetryCount() != null && ev.getRetryCount() >= maxAttempts) {
            if (eventRepo.tryMarkFailed(ev.getId(), cur, "reach max attempts")) {
                logTransition(ev.getId(), cur, MqttEventStatus.FAILED.name(), "system", "reach max attempts");
            }
            return;
        }

        String sys = ev.getTargetSystem();
        String tid = ev.getTid();

        // 未連線：不送、不計次，只排下一次（等連線恢復）
        if (requireConnected && !isClientConnected(sys)) {
            int backoff = calcBackoffSec(currentAttemptIndex(ev));
            eventRepo.updateNextAttemptTime(ev.getId(), LocalDateTime.now().plusSeconds(backoff));
            //log.debug("[OUTBOX][{}] NOT CONNECTED -> reschedule +{}s", tid, backoff);
            return;
        }

        // 計算「下一次排程」— 僅供失敗/例外路徑使用（RETRYING）
        int nextRetryCount = (ev.getRetryCount() == null ? 0 : ev.getRetryCount()) + 1;
        int backoffSec = calcBackoffSec(Math.max(0, nextRetryCount - 1));
        LocalDateTime nextTime = now.plusSeconds(backoffSec);

        // ★ 決定本次要送出的 payload：
        //   - 第一次（afterCommit）若有 override -> 用原文；
        //   - 其餘（排程/重送） -> 用 DB 內的清洗版（避免把影像塞回 DB）。
        final String payloadForSend = (overridePayloadForThisAttempt != null)
                ? overridePayloadForThisAttempt
                : ev.getPayload();

        try {
            var r = messageSender.send(sys, ev.getEventType(), payloadForSend, MqttMessageType.COMMAND, tid);

            if (r != null && r.isSuccess()) {
                if (Boolean.TRUE.equals(ev.getRequireAck())) {
                    LocalDateTime ackDeadline = now.plusSeconds(Math.max(1, ackTimeoutSec));
                    if (eventRepo.tryMarkSent(ev.getId(), cur, now, ackDeadline)) {
                        logTransition(ev.getId(), cur, MqttEventStatus.SENT.name(), "system",
                                "send ok, wait ack until " + ackDeadline);
                    }
                } else {
                    if (eventRepo.tryMarkSent(ev.getId(), cur, now, now)) {
                        logTransition(ev.getId(), cur, MqttEventStatus.SENT.name(), "system", "send ok");
                    }
                    if (eventRepo.tryMarkAckedByTid(tid, now, "no-ack required")) {
                        eventRepo.findByTid(tid).ifPresent(found ->
                                logTransition(found.getId(),
                                        MqttEventStatus.SENT.name(),
                                        MqttEventStatus.ACKED.name(),
                                        "system",
                                        "no-ack"));
                    }
                }
            } else {
                boolean offline = requireConnected && !isClientConnected(sys);
                String msg = (r == null) ? "sender returns null" : r.getMessage();
                if (offline) {
                    eventRepo.updateNextAttemptTime(ev.getId(), nextTime);
                    //log.debug("[OUTBOX][{}] send skipped (OFFLINE) -> reschedule +{}s", tid, backoffSec);
                } else {
                    if (eventRepo.tryMarkRetrying(ev.getId(), cur, nextRetryCount, nextTime, "send fail: " + msg)) {
                        logTransition(ev.getId(), cur, MqttEventStatus.RETRYING.name(), "system", msg);
                    }
                }
            }
        } catch (Exception e) {
            if (requireConnected && !isClientConnected(sys)) {
                eventRepo.updateNextAttemptTime(ev.getId(), nextTime);
                //log.debug("[OUTBOX][{}] exception while OFFLINE -> reschedule +{}s", tid, backoffSec);
                return;
            }
            String msg = e.toString();
            if (eventRepo.tryMarkRetrying(ev.getId(), cur, nextRetryCount, nextTime, "exception: " + msg)) {
                logTransition(ev.getId(), cur, MqttEventStatus.RETRYING.name(), "system", msg);
            }
        }
    }

    /**
     * 收到對應 TID 的 ACK 後結案。
     * 備註：Repository 端應限制僅在 SENT/RETRYING 狀態才會成功更新為 ACKED。
     */
    public void markAcked(String tid, String resultText, String detail) {
        LocalDateTime now = LocalDateTime.now();
        String finalMsg = buildResultMessage(resultText, detail);

        if (eventRepo.tryMarkAckedByTid(tid, now, finalMsg)) {
            eventRepo.findByTid(tid).ifPresent(found ->
                    logTransition(
                            found.getId(),
                            found.getStatus(),              // from 用實際舊值（String）
                            MqttEventStatus.ACKED.name(),   // to
                            "system",
                            "ack received"
                    )
            );
        } else {
            //log.debug("[OUTBOX][{}] ack ignored (not in waiting status)", tid);
        }
    }

    /**
     * ACK 未回且超過截止 → 標記逾時（預設用 FAILED 記「ack-timeout」；如要有獨立 TIMEOUT 狀態，可擴充 enum 與 Repo）
     */
    public void markAckTimeout(MqttEventLog ev) {
        if (!statusEq(ev.getStatus(), MqttEventStatus.SENT)) return;
        if (!Boolean.TRUE.equals(ev.getRequireAck())) return;

        String from = ev.getStatus();
        // 如果你要有獨立狀態，可把這裡換成 TIMEOUT，並在 Repo 加 tryMarkTimeout(...)
        if (eventRepo.tryMarkFailed(ev.getId(), from, "ack-timeout")) {
            logTransition(ev.getId(), from, MqttEventStatus.TIMEOUT.name(), "sweeper", "ack-timeout");
        }
    }

    /* ==================== 內部工具 ==================== */

    private boolean isClientConnected(String sys) {
        try { return mqttClientManager.isConnected(sys); }
        catch (Exception e) {
            log.warn("[OUTBOX] isClientConnected('{}') error: {}", sys, e.toString());
            return false;
        }
    }

    private boolean statusEq(String cur, MqttEventStatus expect) {
        return expect.name().equalsIgnoreCase(cur == null ? "" : cur);
    }

    /** 退避用「目前嘗試索引」：這裡採用 retryCount 值（失敗/例外才會 +1）。 */
    private int currentAttemptIndex(MqttEventLog ev) {
        return Math.max(0, ev.getRetryCount() == null ? 0 : ev.getRetryCount());
    }

    /** 退避：initial * multiplier^(attemptIndex)，上限不超過 maxBackoffSec */
    private int calcBackoffSec(int attemptIndex) {
        double v = initialBackoffSec * Math.pow(backoffMultiplier, attemptIndex);
        return (int) Math.min(Math.round(v), Math.max(initialBackoffSec, maxBackoffSec));
    }

    /** 事件基準時間（優先用 event_time；沒有則用 created_time）是否超過可接受片齡 */
    private boolean isExpired(MqttEventLog ev, LocalDateTime now) {
        LocalDateTime base = (ev.getEventTime() != null) ? ev.getEventTime() : ev.getCreatedTime();
        return base != null && base.isBefore(now.minusSeconds(discardOlderThanSec));
    }

    private void logTransition(Long eventId, String from, String to, String by, String reason) {
        if (eventId == null) return;
        var s = new MqttEventStatusLog();
        s.setEventId(eventId);
        s.setFromStatus(from);
        s.setToStatus(to);
        s.setChangedBy(by);
        s.setChangeReason(reason);
        s.setChangeTime(LocalDateTime.now());
        statusRepo.save(s);
    }

    private static String normalize(String s) {
        return (s == null) ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private String buildResultMessage(String result, String detail) {
        String r = (result == null ? "" : result);
        String d = (detail == null ? "" : detail);
        return (r + (d.isBlank() ? "" : " - " + d)).trim();
    }

    /** 對 payload 做防呆清洗（失敗則回原文，絕不拋例外阻斷流程） */
    private String safelySanitize(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            JsonNode safe = payloadSanitizer.sanitizeForLog(node);
            return objectMapper.writeValueAsString(safe);
        } catch (Exception e) {
            log.warn("[OUTBOX] sanitize payload failed, keep raw in DB ({}).", e.toString());
            return raw;
        }
    }
}
