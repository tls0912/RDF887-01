package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.generator.GripperRequestGenerator;
import com.czkuo.rdf88701.application.generator.TransferRequestGenerator;
import com.czkuo.rdf88701.application.generator.WorkingBeamRequestGenerator;
import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class DualGroupOrchestrator {

    @Resource
    private MonitorPoolDispatcher dispatcher;   // 由 Spring 以 bean name 注入：@Component("GP4")、@Component("WB5")、@Component("TR2")… 等
    private final Map<String, GripperRequestGenerator> gpGenerators;
    private final Map<String, WorkingBeamRequestGenerator> wbGenerators;
    private final Map<String, TransferRequestGenerator> trGenerators;

    // ─────────────────────────────────────────────────────────────
    // Resource locks (in-memory)
    // 目的：避免跨 Group 的 TR/WB/GP 同時判斷/建單，造成 race condition
    // 設計：每次只拿 1 把鎖（1 key）→ 不會死鎖
    // ─────────────────────────────────────────────────────────────
    private final ConcurrentHashMap<String, AtomicBoolean> locks = new ConcurrentHashMap<>();

    private boolean tryLock(String key) {
        AtomicBoolean b = locks.computeIfAbsent(key, k -> new AtomicBoolean(false));
        return b.compareAndSet(false, true);
    }

    private void unlock(String key) {
        AtomicBoolean b = locks.get(key);
        if (b != null) b.set(false);
    }

    /**
     * 決定每個 device 需要鎖的共享資源 key（可回多把鎖）
     * <p>
     * 規則：
     * - TR4 只鎖 TR4_VS_WB
     * - TR5 只鎖 TR5_VS_WB
     * - WB5/WB6 同時鎖：
     * - TR4_VS_WB
     * - TR5_VS_WB
     * - WB5_VS_WB6（避免梁彼此同時判斷）
     * - 其他裝置預設鎖自己
     */
    private List<String> lockKeysFor(String deviceName) {

        // --- Cover transfers ---
//        if ("TR4".equals(deviceName)) {
//            return List.of("LOCK:TR4_VS_TR5");
//        }
//        if ("TR5".equals(deviceName)) {
//            return List.of("LOCK:TR4_VS_TR5");
//        }

//        // --- Cover transfers ---
//        if ("TR4".equals(deviceName)) {
//            return List.of("LOCK:TR4_VS_WB");
//        }
//        if ("TR5".equals(deviceName)) {
//            return List.of("LOCK:TR5_VS_WB");
//        }
//        // --- Working beams coupled with TR4/TR5 ---
//        if ("WB5".equals(deviceName) || "WB6".equals(deviceName)) {
//            return List.of(
//                    "LOCK:TR4_VS_WB",
//                    "LOCK:TR5_VS_WB",
//                    "LOCK:WB5_VS_WB6"
//            );
//        }

        // 預設：鎖自己
        return List.of("DEV:" + deviceName);
    }

    // ───── Group A：GP4 → WB8 → WB5 ─────
    private final AtomicBoolean groupARunning = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 50, initialDelay = 100)
    public void tickGroupA() {
        if (!groupARunning.compareAndSet(false, true)) return;
        dispatcher.submit("[dispatcher] tickGroupA", () ->
        {
            try {
                runGP(4L);   // GP4
              //  runWB(3L);   // WB3
                runWB(5L);   // WB5
                runWB(8L);   // WB8
            } catch (Throwable t) {
                log.error("[ORCH-A] tick error", t);
            } finally {
                groupARunning.set(false);
            }
        });
    }

    // ───── Group B：GP5 → WB6 ─────
    private final AtomicBoolean groupBRunning = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 50, initialDelay = 100)
    public void tickGroupB() {
        if (!groupBRunning.compareAndSet(false, true)) return;
        dispatcher.submit("[dispatcher] tickGroupB", () ->
        {
            try {
                runGP(5L);   // GP5
              //  runWB(4L);   // WB4
                runWB(6L);   // WB6
            } catch (Throwable t) {
                log.error("[ORCH-B] tick error", t);
            } finally {
                groupBRunning.set(false);
            }
        });
    }

    // ───── Group C：GP2 → WB7 ─────
    private final AtomicBoolean groupCRunning = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 100, initialDelay = 100)
    public void tickGroupC() {
        if (!groupCRunning.compareAndSet(false, true)) return;
        dispatcher.submit("[dispatcher] tickGroupC", () ->
        {
            try {
                runGP(2L);   // GP2
                runWB(7L);   // WB7
            } catch (Throwable t) {
                log.error("[ORCH-C] tick error", t);
            } finally {
                groupCRunning.set(false);
            }
        });
    }

    // ───── Group D：TR2 → WB1 ─────
    private final AtomicBoolean groupDRunning = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 100, initialDelay = 100)
    public void tickGroupD() {
        if (!groupDRunning.compareAndSet(false, true)) return;
        dispatcher.submit("[dispatcher] tickGroupD", () ->
        {
            try {
                runTR(2L);   // TR2
                runWB(1L);   // WB1
            } catch (Throwable t) {
                log.error("[ORCH-D] tick error", t);
            } finally {
                groupDRunning.set(false);
            }
        });
    }

    // ───── Group E：TR4 ─────
    private final AtomicBoolean groupERunning = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 200, initialDelay = 100)
    public void tickGroupE() {
        if (!groupERunning.compareAndSet(false, true)) return;
        dispatcher.submit("[dispatcher] tickGroupE", () ->
        {
            try {
                runTR(4L);   // TR4
            } catch (Throwable t) {
                log.error("[ORCH-E] tick error", t);
            } finally {
                groupERunning.set(false);
            }
        });
    }

    // ───── Group F：TR5 ─────
    private final AtomicBoolean groupFRunning = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 200, initialDelay = 100)
    public void tickGroupF() {
        if (!groupFRunning.compareAndSet(false, true)) return;
        dispatcher.submit("[dispatcher] tickGroupF", () ->
        {
            try {
                runTR(5L);   // TR5
            } catch (Throwable t) {
                log.error("[ORCH-F] tick error", t);
            } finally {
                groupFRunning.set(false);
            }
        });
    }

    // ───── Helpers ─────

    private void runGP(Long id) {
        String name = "GP" + id;
        var gen = gpGenerators.get(name);
        if (gen == null) {
            //log.debug("[ORCH] skip {}: no generator", name);
            return;
        }
        safeRunWithLocks(() -> gen.generateRequest(id), name);
    }

    private void runWB(Long id) {
        String name = "WB" + id;
        var gen = wbGenerators.get(name);
        if (gen == null) {
            //log.debug("[ORCH] skip {}: no generator", name);
            return;
        }
        safeRunWithLocks(() -> gen.generateRequest(id), name);
    }

    private void runTR(Long id) {
        String name = "TR" + id;
        var gen = trGenerators.get(name);
        if (gen == null) {
            //log.debug("[ORCH] skip {}: no generator", name);
            return;
        }
        safeRunWithLocks(() -> gen.generateRequest(id), name);
    }

    /**
     * 一次取得多把鎖（固定排序避免死鎖）
     * - 拿不到任何一把就放棄本輪（並釋放已拿到的）
     */
    private void safeRunWithLocks(Supplier<Optional<Long>> fn, String deviceName) {
        List<String> keys = new ArrayList<>(lockKeysFor(deviceName));
        Collections.sort(keys);

        List<String> acquired = new ArrayList<>(keys.size());
        for (String k : keys) {
            if (!tryLock(k)) {
                for (int i = acquired.size() - 1; i >= 0; i--)
                    unlock(acquired.get(i));
                //log.debug("[ORCH] skip {}: locked by {}", deviceName, k);
                return;
            }
            acquired.add(k);
        }

        try {
            safeRun(fn, deviceName);
        } finally {
            for (int i = acquired.size() - 1; i >= 0; i--)
                unlock(acquired.get(i));
        }
    }

    private void safeRun(Supplier<Optional<Long>> fn, String name) {
        try {
            fn.get().ifPresent(reqId -> log.info("[ORCH] {} created reqId={}", name, reqId));
        } catch (Throwable t) {
            log.warn("[ORCH] {} error: {}", name, t.getMessage(), t);
        }
    }
}
