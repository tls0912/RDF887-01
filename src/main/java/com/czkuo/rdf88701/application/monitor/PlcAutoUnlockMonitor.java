package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.AmrInterlockService;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * PLC 自動解鎖 Monitor（備援，不建立帳籍）
 *
 * 規則：
 * - 監控 STK03/04/05（R008 流）→ 對應位置：STK03→Site#1, STK04→Transfer#2, STK05→Site#17
 * - 若「該位置連續有帳」 >= autoUnlockAfterSeconds（預設 60 秒），則呼叫 PLC disablePick(port) 解鎖
 * - 有 cooldownSeconds（預設 45 秒）避免猛送
 *
 * 不做的事：
 * - 不建立帳（不呼叫 LocationAccountingService.entry）
 * - 不寫在位快照
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcAutoUnlockMonitor {

    private final AmrInterlockService interlock;
    private final LocationTrackingRepository locationTrackingRepository;

    @Value("${app.a015.plc.watchPorts:STK03,STK04,STK05}")
    private String watchPortsCsv;

    @Value("${app.a015.plc.autoUnlockAfterSeconds:60}")
    private long autoUnlockAfterSeconds;

    @Value("${app.a015.plc.cooldownSeconds:45}")
    private long cooldownSeconds;

    /** Port → Location 別名 */
    private static final Map<String, String> PORT_TO_LOCATION = Map.of(
            "STK03", "Site#1",
            "STK04", "Transfer#2",
            "STK05", "Site#17"
    );

    /** 每個 Port 連續「無帳」的起始時間 */
    private final Map<String, Instant> noAccountSince = new HashMap<>();

    /** 每個 Port 上次嘗試解鎖時間（冷卻用途） */
    private final Map<String, Instant> lastUnlockTriedAt = new HashMap<>();

    private List<String> ports() {
        String[] arr = Optional.ofNullable(watchPortsCsv).orElse("").split("[,;\\s]+");
        List<String> list = new ArrayList<>();
        for (String a : arr) {
            if (a != null && !a.isBlank()) list.add(a.trim().toUpperCase());
        }
        if (list.isEmpty()) list = List.of("STK03", "STK04", "STK05");
        return list;
    }

    /** 週期性偵測（預設每 10s；可用 app.a015.plc.pollIntervalMs 覆寫） */
    @Scheduled(fixedDelayString = "${app.a015.plc.pollIntervalMs:10000}")
    public void pollAndAutoUnlock() {
        for (String port : ports()) {
            String location = PORT_TO_LOCATION.get(port);
            if (location == null) {
                //log.debug("[PLC-AUTO-UNLOCK] 未知 port→location 映射，略過：{}", port);
                continue;
            }

            try {
                boolean hasAccount = locationTrackingRepository.hasContainerAtLocationName(location);

                if (hasAccount) {
                    // 有帳 → 累計時間
                    Instant start = noAccountSince.computeIfAbsent(port, p -> Instant.now());
                    long seconds = Duration.between(start, Instant.now()).getSeconds();

                    // 冷卻判斷
                    Instant lastTried = lastUnlockTriedAt.get(port);
                    boolean inCooldown = lastTried != null &&
                            Duration.between(lastTried, Instant.now()).getSeconds() < cooldownSeconds;

                    if (seconds >= autoUnlockAfterSeconds && !inCooldown) {
                        log.warn("[PLC-AUTO-UNLOCK] 觸發：port={}, location={}, 無帳持續={}s", port, location, seconds);
                        try {
                            boolean ok = interlock.disableDrop(port); // 清 pass-enable=0
                            lastUnlockTriedAt.put(port, Instant.now());
                            if (!ok) {
                                log.warn("[PLC-AUTO-UNLOCK] disablePick 失敗：port={}, location={}", port, location);
                            } else {
                                log.info("[PLC-AUTO-UNLOCK] 成功：port={}, location={}", port, location);
                            }
                            // 成功/失敗皆進入冷卻；不更動 noAccountSince，直到真的偵測到「有帳」再清除
                        } catch (Exception ex) {
                            lastUnlockTriedAt.put(port, Instant.now());
                            log.warn("[PLC-AUTO-UNLOCK] 例外：port={}, location={}, err={}", port, location, ex.getMessage(), ex);
                        }
                    }
                } else {
                    // 一旦有帳 → 重置計時
                    noAccountSince.remove(port);
                }

            } catch (Exception ex) {
                // 查詢「有無帳」失敗 → 不做解鎖，避免誤清；保留當前計時
                //log.debug("[PLC-AUTO-UNLOCK] 檢查失敗：port={}, location={}, err={}", port, location, ex.getMessage());
            }
        }
    }
}
