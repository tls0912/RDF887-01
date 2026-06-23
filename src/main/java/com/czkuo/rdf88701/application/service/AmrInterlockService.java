package com.czkuo.rdf88701.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

/**
 * AMR 取料 Interlock 協調服務（硬編位址/bit；不經由設定檔）
 *
 * 規則：
 *  - W1015 = pass-enable（我們寫入，讓 PLC 允許 AMR 取）
 *  - W0015 = interlock 狀態（PLC 回覆：0=不可取、1=可取）
 *  - STK03/04/05 → bit 10/11/12（a/b/c 對應 0cba）
 *
 * 備註：
 *  - address 以「word.bit」形式組合（如：W0015.10）。
 *    若你的 PlcSafeAccess 用別的格式，請改 BIT_SEP 常數或 bitAddr(...)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmrInterlockService {

    private final PlcAccessService plc;   // 直接復用你現有的通用 I/O 包裝

    // ----- 硬編常數 -----
    private static final String PLC_DEVICE   = "PLC-Main";
    private static final String WORD_STATUS  = "W1015";  // PLC 回覆：0/1
    private static final String WORD_ENABLE  = "W0015";  // 我們寫入：1=允許
    private static final long   POLL_INTERVAL_MS = 50;  // 輪詢間隔
    private static final String BIT_SEP = ".";           // 位址連接符，ex: W0015.10

    // STK -> bit 對應（a/b/c = 0/1/2）
    private static final Map<String, Integer> BIT_MAP = Map.of(
            "STK03", 0,
            "STK04", 1,
            "STK05", 2
    );

    /** 發出「允許放料」請求 */
    public boolean enableDrop(String stkPort) {
        Integer bit = bitOf(stkPort);
        if (bit == null) {
            log.warn("[Interlock] 未支援的 Port：{}", stkPort);
            return false;
        }
        try {
            int cur = plc.readUInt16(PLC_DEVICE, WORD_ENABLE);
            int next = cur | (1 << bit);
            if (next != cur) {
                plc.writeUInt16(PLC_DEVICE, WORD_ENABLE, next);
                log.info("[Interlock][{}] PC->{} set bit{} = 1", stkPort, WORD_ENABLE, bit);
            } else {
                //log.debug("[Interlock][{}] PC->{} bit{} 已是 1，略過寫入", stkPort, WORD_ENABLE, bit);
            }
            return true;
        } catch (Exception e) {
            log.error("[Interlock] 置位失敗：word={}, bit={}, err={}", WORD_ENABLE, bit, e.getMessage(), e);
            return false;
        }
    }

    /** 清除「允許放料」請求（通常在 AMR 離開時呼叫） */
    public boolean disableDrop(String stkPort) {
        Integer bit = bitOf(stkPort);
        if (bit == null) return false;
        try {
            int cur = plc.readUInt16(PLC_DEVICE, WORD_ENABLE);
            int next = cur & ~(1 << bit);
            if (next != cur) {
                plc.writeUInt16(PLC_DEVICE, WORD_ENABLE, next);
                log.info("[Interlock][{}] PC->{} set bit{} = 0", stkPort, WORD_ENABLE, bit);
            } else {
                //log.debug("[Interlock][{}] PC->{} bit{} 已是 0，略過寫入", stkPort, WORD_ENABLE, bit);
            }
            return true;
        } catch (Exception e) {
            log.error("[Interlock][{}] 清位失敗：word={}, bit={}, err={}", stkPort, WORD_ENABLE, bit, e.getMessage(), e);
            return false;
        }
    }

    /** 讀一次 PLC 狀態（查對應 bit 是否為 1） */
    public boolean isEnable(String stkPort) {
        Integer bit = bitOf(stkPort);
        if (bit == null) return false;
        int w = plc.readUInt16(PLC_DEVICE, WORD_ENABLE);
        boolean ok = ((w >> bit) & 0x1) == 1;
        //log.debug("[Interlock][{}] PC->{} bit{} = {}", stkPort, WORD_STATUS, bit, ok ? 1 : 0);
        return ok;
    }

    /** 讀一次 PLC 狀態（查對應 bit 是否為 1） */
    public boolean isReady(String stkPort) {
        Integer bit = bitOf(stkPort);
        if (bit == null) return false;
        int w = plc.readUInt16(PLC_DEVICE, WORD_STATUS);
        boolean ok = ((w >> bit) & 0x1) == 1;
        //log.debug("[Interlock][{}] PLC->{} bit{} = {}", stkPort, WORD_STATUS, bit, ok ? 1 : 0);
        return ok;
    }

    /**
     * 等待 PLC 狀態為 1（W1015 對應 bit==1）直到 timeoutMs
     * - 使用 ScheduledExecutorService 輪詢，避免 while+sleep 的 busy-waiting
     */
    public boolean waitReady(String stkPort, long timeoutMs) {
        Integer bit = bitOf(stkPort);
        if (bit == null) return false;

        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "amr-interlock-waiter");
            t.setDaemon(true);
            return t;
        });

        CompletableFuture<Boolean> done = new CompletableFuture<>();

        ScheduledFuture<?> poll = ses.scheduleWithFixedDelay(() -> {
            try {
                int w = plc.readUInt16(PLC_DEVICE, WORD_STATUS);
                if (((w >> bit) & 0x1) == 1) {
                    done.complete(true);
                }
            } catch (Exception e) {
                // 讀失敗先忽略下一輪
                log.warn("[Interlock][{}] 讀取 {} 失敗：{}", stkPort, WORD_STATUS, e.getMessage());
            }
        }, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);

        ScheduledFuture<?> timeout = ses.schedule(() -> done.complete(false),
                timeoutMs, TimeUnit.MILLISECONDS);

        try {
            return done.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            log.error("[Interlock] 等待例外：{}", e.getMessage(), e);
            return false;
        } finally {
            poll.cancel(true);
            timeout.cancel(true);
            ses.shutdown();
        }
    }

    // ====== 小工具 ======
    private static Integer bitOf(String stkPort) {
        if (stkPort == null) return null;
        return BIT_MAP.get(stkPort.toUpperCase());
    }

    private static String bitAddr(String word, int bit) {
        return word + BIT_SEP + bit; // 例：W0015.10
    }

    private boolean tryWriteBit(String word, int bit, boolean v) {
        try {
            plc.writeBoolean(PLC_DEVICE, word + "." + bit, v); // 首選：.bit
            return true;
        } catch (Exception ex) {
            log.warn("[Interlock] writeBoolean({}.{}) 失敗，退回整字寫：{}", word, bit, ex.getMessage());
            try {
                int w = plc.readUInt16(PLC_DEVICE, word);
                w = v ? (w | (1 << bit)) : (w & ~(1 << bit));
                plc.writeUInt16(PLC_DEVICE, word, w);
                return true;
            } catch (Exception e2) {
                log.error("[Interlock] 整字寫也失敗：word={}, bit={}, {}", word, bit, e2.getMessage(), e2);
                return false;
            }
        }
    }

}
