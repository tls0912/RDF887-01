package com.czkuo.rdf88701.infra.mqtt;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.*;

@Slf4j
@Component
public class InboundDedupRegistry {

    /** Key = (senderSystem, cmdId, tid)；Value 存 payloadHash + 到期時間 */
    private static final class Key {
        final String sys; final String cmd; final String tid;
        Key(String sys, String cmd, String tid) { this.sys = sys; this.cmd = cmd; this.tid = tid; }
        @Override public boolean equals(Object o){ if(this==o) return true; if(!(o instanceof Key k)) return false;
            return Objects.equals(sys,k.sys)&&Objects.equals(cmd,k.cmd)&&Objects.equals(tid,k.tid); }
        @Override public int hashCode(){ return Objects.hash(sys,cmd,tid); }
        @Override public String toString(){ return sys + "|" + cmd + "|" + tid; }
    }
    private static final class Entry { final int hash; final long expAt; Entry(int h,long e){ hash=h; expAt=e; } }

    private final ConcurrentMap<Key, Entry> seen = new ConcurrentHashMap<>();

    @Value("${mqtt.inbox.dedup.ttl-ms:60000}")
    private long dedupTtlMs;

    /**（可選）限制表長，避免極端爆表 */
    @Value("${mqtt.inbox.dedup.max-size:20000}")
    private int maxSize;

    private final ScheduledExecutorService sweeper =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mqtt-inbox-dedup-sweeper"); t.setDaemon(true); return t;
            });

    /** 回傳 true=第一次/已過期/內容改變（應處理）；false=重複（應略過） */
    public boolean firstSeen(String senderSystem, String cmdId, String tid, String payload) {
        final long now = System.currentTimeMillis();
        final Key k = new Key(normSys(senderSystem), normId(cmdId), normId(tid));
        final int ph = (payload==null)?0:payload.hashCode();

        Entry cur = seen.get(k);
        if (cur == null) {
            // 簡單限流：超過 maxSize 就丟掉最舊的（靠 sweeper），這裡仍放入
            seen.put(k, new Entry(ph, now + dedupTtlMs));
            return true;
        }
        if (cur.expAt <= now || cur.hash != ph) {
            // 過期或內容變動：更新為新內容，放行處理一次
            seen.put(k, new Entry(ph, now + dedupTtlMs));
            return true;
        }
        // TTL 內且 payload 相同 → 視為重複
        return false;
    }

    @PostConstruct
    public void start() {
        sweeper.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            seen.entrySet().removeIf(e -> e.getValue().expAt <= now);
            // 粗略保護：極端情況下砍一半
            if (seen.size() > maxSize) {
                // 不嚴格：直接清一輪，交給後續 firstSeen 重建
                log.warn("[MQTT][dedup] oversize={}, purge", seen.size());
                seen.clear();
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        sweeper.shutdownNow();
        seen.clear();
    }

    private static String normSys(String s){ return s==null?"":s.trim().toLowerCase(); }
    private static String normId (String s){ return s==null?"":s.trim(); }
}