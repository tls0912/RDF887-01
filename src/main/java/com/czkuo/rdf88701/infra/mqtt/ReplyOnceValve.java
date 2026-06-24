package com.czkuo.rdf88701.infra.mqtt;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
public class ReplyOnceValve {

    /** Key = (senderSystem, cmdId, tid)；Value = 到期時間戳 */
    private static final class Key {
        final String sys; final String cmd; final String tid;
        Key(String s, String c, String t){ sys=s; cmd=c; tid=t; }
        @Override public boolean equals(Object o){ if(this==o) return true; if(!(o instanceof Key k)) return false;
            return Objects.equals(sys,k.sys)&&Objects.equals(cmd,k.cmd)&&Objects.equals(tid,k.tid); }
        @Override public int hashCode(){ return Objects.hash(sys,cmd,tid); }
        @Override public String toString(){ return sys + "|" + cmd + "|" + tid; }
    }

    private final ConcurrentMap<Key, Long> replied = new ConcurrentHashMap<>();

    /** 同一入站（sender+cmdId+tid）在 TTL 內只允許回覆一次 */
    @Value("${mqtt.reply.once.ttl-ms:60000}")
    private long replyOnceTtlMs;

    private final ScheduledExecutorService sweeper =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mqtt-reply-once-sweeper"); t.setDaemon(true); return t;
            });

    /** 第一次看到 → 放行並記錄；在 TTL 內重複 → 不准回（回傳 false） */
    public boolean shouldReplyOnce(String senderSystem, String cmdId, String tid) {
        final long now = System.currentTimeMillis();
        final Key k = new Key(normSys(senderSystem), normId(cmdId), normId(tid));
        final long exp = now + replyOnceTtlMs;

        Long prev = replied.putIfAbsent(k, exp);
        if (prev == null) return true;            // 第一次
        if (prev <= now) {                        // 過期 → 視為第一次
            replied.replace(k, exp);
            return true;
        }
        return false;                             // TTL 內已回覆過
    }

    /** 收到對應 ACK 後可清掉（可選） */
    public void clear(String senderSystem, String cmdId, String tid) {
        replied.remove(new Key(normSys(senderSystem), normId(cmdId), normId(tid)));
    }

    @PostConstruct
    public void start() {
        sweeper.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            replied.entrySet().removeIf(e -> e.getValue() <= now);
        }, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        sweeper.shutdownNow();
        replied.clear();
    }

    private static String normSys(String s){ return s==null?"":s.trim().toLowerCase(); }
    private static String normId (String s){ return s==null?"":s.trim(); }
}
