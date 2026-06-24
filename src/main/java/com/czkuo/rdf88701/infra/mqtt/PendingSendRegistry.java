package com.czkuo.rdf88701.infra.mqtt;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.*;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Component
public class PendingSendRegistry {

    /** 以 (對方系統, TID, CMD_ID) 當 key，value 為過期時間戳 */
    private static final class Key {
        final String system; final String tid; final String cmdId;
        Key(String system, String tid, String cmdId) {
            this.system = system; this.tid = tid; this.cmdId = cmdId;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(system, k.system) &&
                    Objects.equals(tid, k.tid) &&
                    Objects.equals(cmdId, k.cmdId);
        }
        @Override public int hashCode() { return Objects.hash(system, tid, cmdId); }
        @Override public String toString() { return system + "|" + tid + "|" + cmdId; }
    }

    private final ConcurrentMap<Key, Long> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sweeper =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mqtt-pending-sweeper");
                t.setDaemon(true);
                return t;
            });

    /** 預設 TTL（毫秒）— ACK 在這時間內大多會回來 */
    private volatile long defaultTtlMs = 60_000;

    /** 註冊一筆即將發送（或已發送）的指令，供 ACK 判斷使用 */
    public void markPending(String targetSystem, String tid, String cmdId) {
        long exp = System.currentTimeMillis() + defaultTtlMs;
        pending.put(new Key(norm(targetSystem), tid, cmdId), exp);
        //log.debug("[MQTT][pending] + {}", targetSystem + "|" + tid + "|" + cmdId);
    }

    /** 判斷是否存在未過期的 pending 記錄（用於 resolver 判斷 ACK） */
    public boolean isPendingFrom(String senderSystem, String tid, String cmdId) {
        Long exp = pending.get(new Key(norm(senderSystem), tid, cmdId));
        return exp != null && exp > System.currentTimeMillis();
    }

    /** ACK 已處理，可移除（可選） */
    public void complete(String senderSystem, String tid, String cmdId) {
        pending.remove(new Key(norm(senderSystem), tid, cmdId));
        //log.debug("[MQTT][pending] - {}", senderSystem + "|" + tid + "|" + cmdId);
    }

    /** 週期清掃過期項，避免記憶體累積 */
    @PostConstruct
    public void startSweeper() {
        sweeper.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            pending.entrySet().removeIf(e -> e.getValue() <= now);
        }, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stopSweeper() {
        sweeper.shutdownNow();
        pending.clear();
    }

    public void setDefaultTtlMs(long ttlMs) { this.defaultTtlMs = ttlMs; }

    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(); }
}
