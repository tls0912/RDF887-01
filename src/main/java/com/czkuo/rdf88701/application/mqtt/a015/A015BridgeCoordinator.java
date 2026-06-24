package com.czkuo.rdf88701.application.mqtt.a015;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.A015AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.A015CommandPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

/**
 * A015 橋接協調器（策略#3）
 * ------------------------------------------------------------
 * 場景：
 *   SEEC →(A015:other)→ SAA →(原封不動)→ ASE
 *   ASE  →(A015 ACK:DONE)→ SAA →(轉傳)→ SEEC
 *   SEEC →(A015 ACK:OK)  → SAA →(轉傳)→ ASE
 *
 * 規則：
 *   - 全程使用同一個 TID（由 SEEC 起頭）
 *   - SAA 不改 payload、不改 TID，不做 outbox ack 結案
 *   - 僅在「有橋接 session」的 TID 上做 ACK 轉傳（避免影響策略#1/#2）
 *
 * 實作：
 *   - 以 TID 為 key 維護橋接 session，包含是否已轉傳 DONE/OK 的旗標
 *   - 逾時自動清掉 session（避免堆積）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class A015BridgeCoordinator {

    private final MqttMessageEventPublisher publisher;
    private final MqttMessageLogService logService;
    private final ObjectMapper mapper;

    /** 目標系統代號（對應 MqttClientManager 的連線設定鍵值） */
    @Value("${app.a015.bridge.seec:seec}")
    private String seecSystem;

    @Value("${app.a015.bridge.ase:ase}")
    private String aseSystem;

    /** 橋接 session 的 TTL（毫秒） */
    @Value("${app.a015.bridge.session-ttl-ms:60000}")
    private long sessionTtlMs;

    /** 追蹤橋接狀態（同一 TID） */
    private static final class BridgeState {
        final long expireAt;
        boolean forwardedDoneToSeec = false; // 是否已把 ASE 的 DONE 轉給 SEEC
        boolean forwardedOkToAse    = false; // 是否已把 SEEC 的 OK 轉給 ASE
        BridgeState(long ttlMs) { this.expireAt = System.currentTimeMillis() + ttlMs; }
        boolean expired() { return System.currentTimeMillis() >= expireAt; }
    }

    /** TID → 橋接狀態 */
    private final ConcurrentMap<String, BridgeState> sessions = new ConcurrentHashMap<>();

    /** 背景清理器（daemon） */
    private final ScheduledExecutorService sweeper =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "a015-bridge-sweeper"); t.setDaemon(true); return t;
            });

    @PostConstruct
    public void start() {
        sweeper.scheduleWithFixedDelay(() -> {
            sessions.entrySet().removeIf(e -> e.getValue().expired());
        }, 3, 3, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        sweeper.shutdownNow();
        sessions.clear();
    }

    // ==================== 指令（SEEC → SAA → ASE） ====================

    /**
     * SEEC 發來 A015(other) 時呼叫：建立 session 並原封不動轉發給 ASE
     */
    public void onCommandFromSeec(A015CommandPayload cmd) {
        final String tid = cmd.getTid();
        try {
            sessions.put(tid, new BridgeState(sessionTtlMs)); // 建立/覆蓋 session

            String json = mapper.writeValueAsString(cmd);

            // 落庫（COMMAND）
            logService.record(
                    /*topic*/"cmd/a015",
                    /*sender*/seecSystem,
                    /*receiver*/aseSystem,
                    mapper.readTree(json),
                    MqttMessageType.COMMAND
            );

            // 轉發給 ASE（payload 原封不動）
            publisher.publish(aseSystem, json, MqttMessageType.COMMAND, tid, "A015");
            log.info("[A015][BRIDGE] →ASE（轉發命令）tid={}", tid);

        } catch (Exception e) {
            log.error("[A015][BRIDGE] 轉發 SEEC→ASE 失敗，tid={}, err={}", tid, e.getMessage(), e);
            // 注意：這裡不回 ACK；由上游重送或手動處理
        }
    }

    // ==================== ACK（雙向轉傳） ====================

    /**
     * ASE → SAA（A015 ACK，一般為 RESULT=DONE）時呼叫：
     *   - 僅當存在橋接 session 時，才轉傳給 SEEC
     */
    public void onAckFromAse(A015AckPayload ack) {
        final String tid = ack.getTid();
        BridgeState st = sessions.get(tid);
        if (st == null) {
            //log.debug("[A015][BRIDGE] 忽略 ASE ACK（非橋接 TID）：tid={}", tid);
            return;
        }
        if (st.forwardedDoneToSeec) {
            //log.debug("[A015][BRIDGE] 已轉傳過 ASE→SEEC(DONE)，略過重複：tid={}", tid);
            return;
        }

        try {
            String json = mapper.writeValueAsString(ack);

            // 落庫（ACK）
            logService.record(
                    /*topic*/"ack/a015",
                    /*sender*/aseSystem,
                    /*receiver*/seecSystem,
                    mapper.readTree(json),
                    MqttMessageType.ACK
            );

            // 轉傳給 SEEC（payload 原封不動，同一 TID）
            publisher.publish(seecSystem, json, MqttMessageType.ACK, ack.getTid(), "A015");
            st.forwardedDoneToSeec = true;
            log.info("[A015][BRIDGE] ASE→SEEC ACK 轉傳完成（通常為 DONE），tid={}", tid);

        } catch (Exception e) {
            log.error("[A015][BRIDGE] 轉傳 ASE→SEEC ACK 失敗，tid={}, err={}", tid, e.getMessage(), e);
        }
    }

    /**
     * SEEC → SAA（A015 ACK，一般為 RESULT=OK）時呼叫：
     *   - 僅當存在橋接 session 時，才轉傳給 ASE
     */
    public void onAckFromSeec(A015AckPayload ack) {
        final String tid = ack.getTid();
        BridgeState st = sessions.get(tid);
        if (st == null) {
            //log.debug("[A015][BRIDGE] 忽略 SEEC ACK（非橋接 TID）：tid={}", tid);
            return;
        }
        if (st.forwardedOkToAse) {
            //log.debug("[A015][BRIDGE] 已轉傳過 SEEC→ASE(OK)，略過重複：tid={}", tid);
            return;
        }

        try {
            String json = mapper.writeValueAsString(ack);

            // 落庫（ACK）
            logService.record(
                    /*topic*/"ack/a015",
                    /*sender*/seecSystem,
                    /*receiver*/aseSystem,
                    mapper.readTree(json),
                    MqttMessageType.ACK
            );

            // 轉傳給 ASE（payload 原封不動，同一 TID）
            publisher.publish(aseSystem, json, MqttMessageType.ACK, ack.getTid(), "A015");
            st.forwardedOkToAse = true;
            log.info("[A015][BRIDGE] SEEC→ASE ACK 轉傳完成（通常為 OK），tid={}", tid);

        } catch (Exception e) {
            log.error("[A015][BRIDGE] 轉傳 SEEC→ASE ACK 失敗，tid={}, err={}", tid, e.getMessage(), e);
        }
    }
}
