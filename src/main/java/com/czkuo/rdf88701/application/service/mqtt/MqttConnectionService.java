package com.czkuo.rdf88701.application.service.mqtt;

import com.czkuo.rdf88701.application.mqtt.util.BaseMqttHandlerUtils;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.common.enums.HandshakeReason;
import com.czkuo.rdf88701.common.enums.MqttConnectionStatus;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.command.S001CommandPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S002CommandPayload;
import com.czkuo.rdf88701.domain.repository.MqttConnectionLogRepository;
import com.czkuo.rdf88701.domain.repository.MqttConnectionStateRepository;
import com.czkuo.rdf88701.infra.entity.MqttConnectionLog;
import com.czkuo.rdf88701.infra.entity.MqttConnectionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MqttConnectionService
 * - 管理 MQTT 連線狀態與事件紀錄（狀態快照 + 日誌）
 * - 封裝「等待連線就緒」與「握手直到連上」的同步流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttConnectionService {

    private static final long POLL_INTERVAL_MS = 200L;

    private final MqttConnectionStateRepository connectionStateRepository;
    private final MqttConnectionLogRepository connectionLogRepository;

    // 直接送 MQTT（取代原本透過 MqttCommandService）
    private final MqttDirectMessageSender messageSender;
    private final ObjectMapper objectMapper;

    /* ===== 可由 YAML / 環境變數覆寫的預設參數 ===== */

    /** 每次發完 S001 後，最多等待對方變為 connected 的秒數（預設 5s） */
    @Value("${mqtt.command.handshake.wait-seconds:5}")
    private int defaultHsWaitSeconds;

    /** 連續握手嘗試次數上限（預設 3 次） */
    @Value("${mqtt.command.handshake.attempts:3}")
    private int defaultHsAttempts;

    /** 兩次握手間退避秒數（預設 2s） */
    @Value("${mqtt.command.handshake.backoff-seconds:2}")
    private int defaultHsBackoffSeconds;

    /** 發 S001 時 MESSAGE.Hint 預設值（預設 auto-handshake） */
    @Value("${mqtt.command.handshake.hint:auto-handshake}")
    private String defaultHandshakeHint;

    /* ======================== 狀態更新／查詢 ======================== */

    /** 標記為 CONNECTED；同時更新 lastConnectedTime / lastHeartbeatTime，並寫入事件日誌。 */
    public void markConnected(String remoteSystem, String reason) {
        final String sys = normalize(remoteSystem);
        final LocalDateTime now = LocalDateTime.now();

        MqttConnectionState state = connectionStateRepository
                .findByRemoteSystem(sys)
                .orElseGet(() -> {
                    MqttConnectionState s = new MqttConnectionState();
                    s.setRemoteSystem(sys);
                    s.setCreatedTime(now);
                    return s;
                });

        state.setConnected(true);
        state.setLastConnectedTime(now);
        state.setLastHeartbeatTime(now);
        state.setUpdatedTime(now);
        connectionStateRepository.upsertByRemoteSystem(state);

        writeLog(sys, MqttConnectionStatus.CONNECTED, now, reason);
    }

    /**
     * 收到 S002 ACK：
     *  - 若目前已連線：僅更新 lastHeartbeatTime
     *  - 若目前未連線：直接當作「連線建立」事件處理（轉為 CONNECTED）
     */
    public void refreshHeartbeat(String remoteSystem) {
        final String sys = normalize(remoteSystem);
        final LocalDateTime now = LocalDateTime.now();

        // 嘗試更新心跳時間（若已是連線狀態，多數實作會回 true）
        boolean updated = connectionStateRepository.updateHeartbeatTime(sys, now);
        if (updated) {
            //log.debug("[MQTT] heartbeat updated: {} at {}", sys, now);
            return;
        }

        // 若更新失敗（多半是不存在或非連線狀態）→ 視為由 Heartbeat-ACK 建立連線
        log.info("[MQTT] heartbeat ACK received while DISCONNECTED -> mark {} CONNECTED", sys);
        markConnected(sys, "heartbeat-ack");
    }

    /**
     * 掃描所有已連線對象，若超過 timeout 秒未收到心跳則標記為斷線，
     * 並非阻塞地丟一發 S001 嘗試重連（保留原有行為）。
     */
    public void checkHeartbeatTimeout(long timeoutSeconds) {
        if (timeoutSeconds <= 0) return;
        final List<MqttConnectionState> expired = connectionStateRepository.findExpiredHeartbeat(timeoutSeconds);
        if (expired == null || expired.isEmpty()) return;

        for (MqttConnectionState state : expired) {
            final String sys = state.getRemoteSystem();
            log.warn("[MQTT] heartbeat timeout, mark {} disconnected (> {}s)", sys, timeoutSeconds);
            disconnect(sys, String.format("心跳逾時（>%ds）", timeoutSeconds));
            sendHandshakeS001(sys); // 非阻塞：僅丟一發；真正連上與否，交給後續 ACK/S002 機制判定
        }
    }

    /** 主動標記為斷線並寫入事件日誌。 */
    public void disconnect(String remoteSystem, String reason) {
        final String sys = normalize(remoteSystem);
        if (connectionStateRepository.markAsDisconnected(sys)) {
            log.info("[MQTT] {} marked as DISCONNECTED", sys);
            writeLog(sys, MqttConnectionStatus.DISCONNECTED, LocalDateTime.now(), reason);
        }
    }

    /** 查詢指定系統是否為連線狀態。 */
    public boolean isConnected(String remoteSystem) {
        return connectionStateRepository.isConnected(normalize(remoteSystem));
    }

    /** 回傳所有系統目前連線狀態。 */
    public Map<String, Boolean> getAllConnectionStatus() {
        return connectionStateRepository.getAllConnectionStatusMap();
    }

    /** 取得所有已連線的 remoteSystem 清單。 */
    public List<String> listConnectedPeers() {
        return connectionStateRepository.listCurrentlyConnectedSystems();
    }

    /* ======================== 等待／握手流程 ======================== */

    /**
     * 等待指定對象變為 Connected，最多等待 maxWaitSeconds 秒。
     * @return true = 已連線；false = 超時仍未連線
     */
    public boolean waitUntilConnected(String remoteSystem, long maxWaitSeconds) {
        final String sys = normalize(remoteSystem);
        final long waitSec = Math.max(0, maxWaitSeconds);
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(waitSec);

        while (System.nanoTime() < deadline) {
            if (isConnected(sys)) return true;
            if (!sleepMillis(POLL_INTERVAL_MS)) return false; // 被中斷就退出
        }
        return isConnected(sys);
    }

    /**
     * 以 S001 進行握手，重試直到已連線或超過上限（同步等待）。
     */
    public boolean handshakeUntilConnected(String remoteSystem,
                                           int attempts,
                                           int waitSeconds,
                                           int backoffSeconds,
                                           String hint) {
        final String sys = normalize(remoteSystem);
        if (isConnected(sys)) return true;

        final int maxAttempts = Math.max(1, attempts);
        final int perWait = Math.max(0, waitSeconds);
        final int backoff = Math.max(0, backoffSeconds);
        final String sendHint = buildHandshakeHint(HandshakeReason.STARTUP, hint);
        // final String sendHint = (hint == null || hint.isBlank()) ? defaultHandshakeHint : hint;

        for (int i = 1; i <= maxAttempts; i++) {
            try {
                log.info("[HANDSHAKE] {} attempt {}/{} -> S001 (hint={})", sys, i, maxAttempts, sendHint);

                boolean sentOk = sendS001Direct(sys, sendHint);
                log.info("[HANDSHAKE] {} attempt {}/{} send result: success={}", sys, i, maxAttempts, sentOk);

                if (waitUntilConnected(sys, perWait)) return true;
            } catch (Exception e) {
                log.warn("[HANDSHAKE] {} attempt {}/{} exception: {}", sys, i, maxAttempts, e.toString());
            }

            if (i < maxAttempts && backoff > 0 && !sleepSeconds(backoff)) {
                return false; // 被中斷就退出
            }
        }

        log.error("[HANDSHAKE] {} failed after {} attempt(s)", sys, maxAttempts);
        return false;
    }

    /** 使用預設參數進行握手直到連線（參數來源：mqtt.command.handshake.*）。 */
    public boolean handshakeUntilConnected(String remoteSystem) {
        return handshakeUntilConnected(
                remoteSystem,
                defaultHsAttempts,
                defaultHsWaitSeconds,
                defaultHsBackoffSeconds,
                defaultHandshakeHint
        );
    }

    /* ======================== 工具／記錄 ======================== */

    /** 非阻塞：僅丟一發 S001，不在此等待結果。 */
    public void sendHandshakeS001(String remoteSystem) {
        final String sys = normalize(remoteSystem);
        try {
            boolean ok = sendS001Direct(sys, buildHandshakeHint(HandshakeReason.HB_TIMEOUT, null));
            log.info("[MQTT] S001 sent to {} (success={})", sys, ok);
        } catch (Exception e) {
            log.error("[MQTT] S001 send failed to {} : {}", sys, e.getMessage(), e);
        }
    }

    /** 直接組 S001 payload 並送出（避免依賴 MqttCommandService）。 */
    private boolean sendS001Direct(String targetSystem, String hint) throws Exception {
        final String sys = normalize(targetSystem);
        final String tid = BaseMqttHandlerUtils.generateTid();

        S001CommandPayload payload = new S001CommandPayload();
        payload.setCmd("SYSTEM");
        payload.setCmdId("S001");
        payload.setIdDesc("PC_LINK");
        payload.setTid(tid);

        S001CommandPayload.Message msg = new S001CommandPayload.Message();
        msg.setProgramName(resolveProgramName());
        msg.setVersion(resolveVersion());
        msg.setHint(hint);
        payload.setMessage(msg);

        String json = objectMapper.writeValueAsString(payload);
        MqttSendResult r = messageSender.send(sys, "S001", json, MqttMessageType.COMMAND, tid);
        return r != null && r.isSuccess();
    }

    /** 非阻塞：僅丟一發 S002，不在此等待結果。 */
    public void sendHandshakeS002(String remoteSystem) {
        final String sys = normalize(remoteSystem);
        try {
            boolean ok = sendS002Direct(sys);
            log.info("[MQTT] S002 sent to {} (success={})", sys, ok);
        } catch (Exception e) {
            log.error("[MQTT] S002 send failed to {} : {}", sys, e.getMessage(), e);
        }
    }

    /** 直接組 S002 payload 並送出（避免依賴 MqttCommandService）。 */
    private boolean sendS002Direct(String targetSystem) throws Exception {
        final String sys = normalize(targetSystem);
        final String tid = BaseMqttHandlerUtils.generateTid();

        S002CommandPayload payload = new S002CommandPayload();
        payload.setCmd("SYSTEM");
        payload.setCmdId("S002");
        payload.setIdDesc("CHECK_READY");
        payload.setTid(tid);

        String json = objectMapper.writeValueAsString(payload);
        MqttSendResult r = messageSender.send(sys, "S002", json, MqttMessageType.COMMAND, tid);
        return r != null && r.isSuccess();
    }

    private void writeLog(String remoteSystem, MqttConnectionStatus status, LocalDateTime time, String reason) {
        MqttConnectionLog logEntry = new MqttConnectionLog();
        logEntry.setRemoteSystem(remoteSystem);
        logEntry.setStatus(status.name());
        logEntry.setEventTime(time);
        logEntry.setReason(reason);
        logEntry.setCreatedTime(time);
        connectionLogRepository.save(logEntry);
    }

    private static String normalize(String sys) {
        return (sys == null) ? "" : sys.trim().toLowerCase(Locale.ROOT);
    }

    /** 程式名稱優先取 spring.application.name，否則 fallback "SAA"。 */
    private String resolveProgramName() {
        String v = System.getProperty("spring.application.name");
        return (v != null && !v.isBlank()) ? v : "SAA";
    }

    /** 版本優先取 JAR Manifest Implementation-Version，取不到則 fallback "dev"。 */
    private String resolveVersion() {
        Package pkg = this.getClass().getPackage();
        String v = (pkg != null) ? pkg.getImplementationVersion() : null;
        return (v != null && !v.isBlank()) ? v : "dev";
    }

    /** 依情境組出語意化 hint。 */
    private String buildHandshakeHint(HandshakeReason reason, String extra) {
        // String program = resolveProgramName();
        // String version = resolveVersion();
        // String reasonStr = reason.name().toLowerCase(Locale.ROOT);
        // String base = program + ":" + version + ":" + reasonStr;
        // return (extra == null || extra.isBlank()) ? base : (base + ":" + extra);

        return reason.name().toLowerCase(Locale.ROOT);
    }

    private static boolean sleepMillis(long ms) {
        if (ms <= 0) return true;
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean sleepSeconds(long sec) {
        return sleepMillis(TimeUnit.SECONDS.toMillis(Math.max(0, sec)));
    }
}
