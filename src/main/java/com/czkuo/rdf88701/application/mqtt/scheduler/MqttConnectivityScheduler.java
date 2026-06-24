package com.czkuo.rdf88701.application.mqtt.scheduler;

import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.mqtt.MqttConnectionService;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.config.mqtt.MqttConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MqttConnectivityScheduler
 * -----------------------------------------------------------------------------
 * 目的：
 *   1) 系統啟動時先送「S001（開機/握手）」
 *   2) 之後固定節拍送「S002（心跳）」──不論對端是否已連線，一律嘗試送出
 *   3) 若長時間未收到 S002 ACK，視為斷線，可由 MqttConnectionService 轉為 DISCONNECTED 並依策略發 S001 重連
 *
 * 核心設計：
 *   - 「啟動心跳閘門」(startupS001GateOpen)：預設等「第一發 S001」後才放行 S002
 *   - S002 使用 fixedRate（非 fixedDelay），確保「每 N 毫秒一拍」的穩定節奏
 *   - 可選 jitter，避免多個應用實例同時在同一毫秒打到 broker
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttConnectivityScheduler {

    // 依賴服務 --------------------------------------------------------------

    /** 發送 S001/S002 指令的服務 */
    private final MqttCommandService mqttCommandService;
    /** 維護各對端（peer）連線狀態（CONNECTED/DISCONNECTED）、握手、超時檢查等 */
    private final MqttConnectionService connectionService;
    /** mqtt.connections.* 的設定（例如 peers: ase、seec 等） */
    private final MqttConfigProperties mqttProps;

    // ========================== S001（啟動握手）參數 ==========================

    /** 開機是否自動送 S001；true=是（預設） */
    @Value("${mqtt.auto-report.s001.enabled:true}")
    private boolean s001Enabled;

    /** 系統啟動後延遲幾秒再送第一發 S001（讓 Spring/網路/日誌穩定一下） */
    @Value("${mqtt.auto-report.s001.delay-seconds:5}")
    private int s001DelaySeconds;

    /** 目標對端：ALL / ASE / SEEC / BOTH（從 mqtt.connections.* 裡選） */
    @Value("${mqtt.auto-report.s001.target:ALL}")
    private String s001Target;

    /** S001 的備註/提示字串（方便對端或審計辨識來源） */
    @Value("${mqtt.auto-report.s001.hint:auto-startup}")
    private String s001Hint;

    /** 僅在對端目前為 DISCONNECTED 才送 S001；本需求通常設 false（總是送） */
    @Value("${mqtt.auto-report.s001.only-when-disconnected:false}")
    private boolean s001OnlyWhenDisconnected;

    /** 是否使用「握手直到連上」的模式：會做多次重試/等待 ACK；否則以 fire-and-forget 方式送幾次就結束 */
    @Value("${mqtt.auto-report.s001.use-handshake-until-connected:true}")
    private boolean useHandshakeUntilConnected;

    /** 啟動時先把目標對端標記為 DISCONNECTED（避免上次非正常關閉造成的假連線） */
    @Value("${mqtt.auto-report.s001.reset-state-on-startup:true}")
    private boolean resetStateOnStartup;

    /** fire-and-forget 模式：S001 最多嘗試幾次 */
    @Value("${mqtt.auto-report.s001.max-attempts:3}")
    private int s001MaxAttempts;

    /** fire-and-forget 模式：每次失敗後的等待秒數 */
    @Value("${mqtt.auto-report.s001.backoff-seconds:5}")
    private int s001BackoffSeconds;

    /** 握手模式：最多嘗試幾次 */
    @Value("${mqtt.command.handshake.attempts:3}")
    private int hsAttempts;

    /** 握手模式：每次嘗試後等待 ACK 的秒數 */
    @Value("${mqtt.command.handshake.wait-seconds:5}")
    private int hsWaitSeconds;

    /** 握手模式：兩次嘗試之間的退避秒數 */
    @Value("${mqtt.command.handshake.backoff-seconds:2}")
    private int hsBackoffSeconds;

    // ========================== S002（心跳）參數 ==========================

    /** 是否啟用心跳排程 */
    @Value("${mqtt.heartbeat.enabled:true}")
    private boolean hbEnabled;

    /** 每次送心跳前，額外隨機延遲（秒）；0=不延遲，用於多實例錯開節拍 */
    @Value("${mqtt.heartbeat.jitter-seconds:0}")
    private int hbJitterSeconds;

    /** 心跳逾時（秒）：>0 生效，超過此秒數未收 ACK → 視為斷線 */
    @Value("15")
    private long hbTimeoutSeconds;

    /**
     * 是否「等 S001 才開始 S002」。
     * true（預設）：等「第一發 S001」就開閘放行心跳；若 S001 卡住，也有保險計時器會強制開閘。
     * false：不等待，應用啟動後心跳即以固定節拍開始送。
     */
    @Value("${mqtt.heartbeat.wait-startup-s001:true}")
    private boolean hbWaitStartupS001;

    /** S001 若卡住，最多延遲（啟動延遲 + 本秒數）後，強制開閘，避免心跳永遠不發 */
    @Value("${mqtt.heartbeat.startup-gate-max-wait-seconds:20}")
    private int hbStartupGateMaxWaitSeconds;

    /** 啟動心跳的「閘門」：true=允許 S002 排程送出；false=先擋住（等 S001） */
    private final AtomicBoolean startupS001GateOpen = new AtomicBoolean(false);

    // ========================== 開機流程 ==========================

    /**
     * Spring Boot 啟動完成後觸發。
     * 主要流程：
     *   1) 解析目標對端清單（ASE/SEEC/ALL）
     *   2) （可選）先把目標設為 DISCONNECTED，清掉上次的假連線
     *   3) 若 s001Enabled=true → 啟動一個 Thread 送 S001（第一發就開閘放行心跳）
     *   4) 啟動「保險 Thread」：若 S001 遲遲沒開閘，超時後強制開
     *   5) 若設定不等待 S001 → 直接開閘讓 S002 開始
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        final List<String> targets = resolveTargets(s001Target);
        if (targets.isEmpty()) {
            // 沒有任何 mqtt.connections；為避免 S002 永遠等不到開閘，直接開閘放行
            log.warn("[S001] no mqtt.connections configured; skip S001. Heartbeat gate opened to avoid stall.");
            startupS001GateOpen.set(true);
            return;
        }

        // 啟動時先把目標對端標記為 DISCONNECTED（可關閉）
        if (resetStateOnStartup) {
            for (String sys : targets) {
                try { connectionService.disconnect(sys, "startup reset"); }
                catch (Exception e) { log.warn("[CONNECTIVITY] reset {} failed: {}", sys, e.toString()); }
            }
            log.info("[CONNECTIVITY] startup-reset-disconnected: {}", targets);
        }

        // 若關閉 S001，自動開閘，讓 S002 正常跑
        if (!s001Enabled) {
            log.info("[S001] disabled; opening heartbeat gate immediately.");
            startupS001GateOpen.set(true);
            return;
        }

        final String program = resolveProgramName();
        final String version = resolveVersion();
        log.info("[S001] will auto-report in {}s -> targets={}, program={}, version={}, hint={}, onlyWhenDisconnected={}, useHandshake={}, gateMaxWait={}s",
                s001DelaySeconds, targets, program, version, s001Hint, s001OnlyWhenDisconnected, useHandshakeUntilConnected, hbStartupGateMaxWaitSeconds);

        // 啟動 S001 thread：避免阻塞 Spring 主線程
        new Thread(() -> {
            sleepSeconds(s001DelaySeconds);  // 啟動緩衝

            boolean gateOpened = false;      // 避免重複開閘（只需第一次 S001 觸發即開）

            for (String sys : targets) {
                // 若只在 DISCONNECTED 時才送 S001且當前已連線，則跳過（大多數情境建議 always send）
                if (s001OnlyWhenDisconnected && connectionService.isConnected(sys)) {
                    log.info("[S001][{}] already connected; skip.", sys);
                    continue;
                }

                if (useHandshakeUntilConnected) {
                    // 握手模式：會多次嘗試+等待 ACK
                    // 第一發 S001 就開閘（不必等握手全部完成），讓心跳開始節拍
                    if (hbWaitStartupS001 && !gateOpened) {
                        startupS001GateOpen.set(true);
                        gateOpened = true;
                        log.info("[HEARTBEAT] gate opened after FIRST S001 kick (handshake mode).");
                    }

                    boolean ok = connectionService.handshakeUntilConnected(
                            sys, hsAttempts, hsWaitSeconds, hsBackoffSeconds, s001Hint);
                    log.info("[S001][{}] handshake-until-connected -> {}", sys, ok ? "CONNECTED" : "FAILED");

                } else {
                    // fire-and-forget 模式：嘗試 N 次 S001，不等待 ACK 最終結果
                    boolean ok = false;
                    final int attempts = Math.max(1, s001MaxAttempts);
                    final int backoff = Math.max(0, s001BackoffSeconds);
                    for (int attempt = 1; attempt <= attempts; attempt++) {
                        try {
                            MqttSendResult r = mqttCommandService.sendS001(sys, program, version, s001Hint);
                            ok = (r != null && r.isSuccess());
                            log.info("[S001][{}] attempt {}/{} -> success={}, tid={}, msg={}",
                                    sys, attempt, attempts,
                                    (r != null && r.isSuccess()),
                                    (r != null ? r.getTid() : "n/a"),
                                    (r != null ? r.getMessage() : "n/a"));

                            // 第一發就開閘（不必等到循環全部完成）
                            if (hbWaitStartupS001 && !gateOpened) {
                                startupS001GateOpen.set(true);
                                gateOpened = true;
                                log.info("[HEARTBEAT] gate opened after FIRST S001 publish (fire-and-forget).");
                            }

                            if (ok) break;  // 成功就不再重試
                        } catch (Exception e) {
                            log.warn("[S001][{}] attempt {}/{} threw: {}", sys, attempt, attempts, e.toString());
                        }
                        sleepSeconds(backoff);
                    }
                    if (!ok) log.error("[S001][{}] all attempts failed.", sys);
                }
            }

            // 冪等保險：S001 迴圈跑完再開一次閘（如果先前已開，不影響）
            if (hbWaitStartupS001) {
                startupS001GateOpen.set(true);
                log.info("[HEARTBEAT] startup gate opened after S001 loop finished.");
            }
        }, "startup-s001-runner").start();

        // 另一道保險：若 S001 遲遲沒開閘，超過「啟動延遲 + 指定秒數」就強制開閘
        new Thread(() -> {
            int maxWait = Math.max(0, s001DelaySeconds + hbStartupGateMaxWaitSeconds);
            sleepSeconds(maxWait);
            if (hbWaitStartupS001 && !startupS001GateOpen.get()) {
                startupS001GateOpen.set(true);
                log.warn("[HEARTBEAT] startup gate forced open after {}s.", maxWait);
            }
        }, "startup-heartbeat-gate-fallback").start();

        // 若設定為「不等待 S001」，就直接開閘
        if (!hbWaitStartupS001) {
            startupS001GateOpen.set(true);
            log.info("[HEARTBEAT] startup gate pre-opened (wait-startup-s001=false).");
        }
    }

    // ========================== 心跳排程 ==========================

    /**
     * 每固定速率（fixedRate）送一次 S002（預設 5s 一拍）
     * 說明：
     *   - fixedRate：以「開始時間」計算節拍，上一輪執行時間不會拖慢下一輪排程
     *   - 本版一律對 mqtt.connections 中的所有對端送，不檢查 connected 狀態
     *   - 若 hbWaitStartupS001=true，會在「啟動心跳閘門」開啟後才開始送
     */
    @Scheduled(fixedRateString = "${mqtt.heartbeat.fixed-rate-ms:60000}", scheduler = "mqttScheduler")
    public void reportHeartbeat() {
        if (!hbEnabled) return;                        // 關閉心跳時直接跳出
        if (hbWaitStartupS001 && !startupS001GateOpen.get()) return; // 閘門未開 → 等

        final List<String> targets = new ArrayList<>(mqttProps.getConnections().keySet());
        if (targets.isEmpty()) {
            // 若沒有任何對端（例如設定檔缺失），心跳 tick 會被跳過，這裡打 DEBUG 方便排查
            //log.debug("[S002] tick skipped: no targets (mqtt.connections empty)");
            return;
        }

        // 每一拍先印出節拍與目標，可在 DEBUG 等級看到穩定的 5 秒節奏
        //log.debug("[S002] tick: targets={}, gateOpen={}", targets, startupS001GateOpen.get());

        for (String sys : targets) {
            try {
                applyJitterIfAny();               // 多實例部署時可用來「打散」同一毫秒的併發
                connectionService.sendHandshakeS002(sys); // 不論連線狀態，一律嘗試送
            } catch (Exception e) {
                log.warn("[S002][{}] send failed: {}", sys, e.toString());
            }
        }
    }

    /**
     * 心跳逾時巡檢：> timeout 秒未收到 S002 ACK → 視為斷線
     * 實際的判定/轉態/重連策略由 MqttConnectionService.checkHeartbeatTimeout(...) 處理
     */
    // @Scheduled(fixedDelayString = "${mqtt.heartbeat.timeout-check-ms:100000}", scheduler = "mqttScheduler")
    public void checkHeartbeatTimeout() {
        if (!hbEnabled) return;
        if (hbTimeoutSeconds <= 0) return; // 不啟用逾時機制
        try {
            connectionService.checkHeartbeatTimeout(hbTimeoutSeconds);
        } catch (Exception e) {
            log.warn("[HEARTBEAT] timeout check failed: {}", e.toString());
        }
    }

    // ========================== 私有工具 ==========================

    /**
     * 解析 s001Target 成實際目標對端清單。
     * 若指定 BOTH 而設定中缺某個對端，會退而取有存在的；若兩個都沒有，回傳全部 keys（通常為空）。
     */
    private List<String> resolveTargets(String t) {
        Set<String> keys = mqttProps.getConnections().keySet(); // 已正規化為小寫
        String v = (t == null) ? "all" : t.trim().toLowerCase(Locale.ROOT);
        if ("seec".equals(v)) return List.of("seec");
        if ("ase".equals(v))  return List.of("ase");
        if ("both".equals(v)) {
            List<String> list = new ArrayList<>();
            if (keys.contains("seec")) list.add("seec");
            if (keys.contains("ase"))  list.add("ase");
            if (list.isEmpty()) list.addAll(keys);
            return list;
        }
        return new ArrayList<>(keys); // ALL
    }

    /** 依設定加入 0..jitter 秒的隨機等待，用於多實例錯峰 */
    private void applyJitterIfAny() {
        int j = Math.max(0, hbJitterSeconds);
        if (j <= 0) return;
        int wait = ThreadLocalRandom.current().nextInt(j + 1);
        sleepSeconds(wait);
    }

    /** 程式名稱：優先取 spring.application.name，否則回傳 "SAA" */
    private String resolveProgramName() {
        String v = System.getProperty("spring.application.name");
        return (v != null && !v.isBlank()) ? v : "SAA";
    }

    /** 版本號：優先取 Package Implementation-Version（由打包時填入），否則回傳 "dev" */
    private String resolveVersion() {
        Package pkg = this.getClass().getPackage();
        String v = (pkg != null) ? pkg.getImplementationVersion() : null;
        return (v != null && !v.isBlank()) ? v : "dev";
    }

    /** 安全睡眠工具：忽略非正秒數，並在中斷時恢復中斷旗標 */
    private static void sleepSeconds(int s) {
        if (s <= 0) return;
        try { TimeUnit.SECONDS.sleep(s); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
