package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.PortLockUnlock.PortLockUnlockSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQuerySecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class ZipAutoUnlockMonitor {

    private final ZipStockerCommandService zip;

    @Value("${app.a015.zip.target:ZIPA}")
    private String zipTargetName;

    /** 監控的 Port 名稱；預設 STK01~STK05（R007: 01/02） */
    @Value("${app.a015.zip.watchPorts:STK01,STK02}")
    private String watchPortsCsv;

    /** 持續鎖定且無載具超過此秒數才觸發解鎖 */
    @Value("${app.a015.zip.autoUnlockAfterSeconds:60}")
    private long autoUnlockAfterSeconds;

    /** 成功或嘗試解鎖後的冷卻時間，避免重複猛送 */
    @Value("${app.a015.zip.cooldownSeconds:45}")
    private long cooldownSeconds;

    /** 每個 Port 的「開始觀測鎖定而且無載具」的時間點 */
    private final Map<String, Instant> lockedNoCarrierSince = new HashMap<>();

    /** 每個 Port 上次嘗試解鎖的時間點（用於冷卻） */
    private final Map<String, Instant> lastUnlockTriedAt = new HashMap<>();

    private List<String> ports() {
        String[] arr = Optional.ofNullable(watchPortsCsv).orElse("").split("[,;\\s]+");
        List<String> list = new ArrayList<>();
        for (String a : arr) {
            if (a != null && !a.isBlank()) list.add(a.trim().toUpperCase());
        }
        return list.isEmpty() ? List.of("STK01","STK02") : list;
    }

    @Scheduled(fixedDelayString = "${app.a015.zip.pollIntervalMs:10000}")
    public void pollAndAutoUnlock() {
        ZipTarget target = ZipTarget.valueOf(zipTargetName.toUpperCase());
        List<String> ports = ports();
        for (String port : ports) {
            try {
                Root<StatusQuerySecondaryBody> resp = zip.queryPorts(target, port);
                PortState st = parsePortState(resp, port);

                if (st == null) {
                    // 無法解析狀態：重置狀態以避免誤觸發
                    lockedNoCarrierSince.remove(port);
                    continue;
                }

                if (st.locked && (st.carrierId == null || st.carrierId.isBlank())) {
                    // 開始或持續計時
                    lockedNoCarrierSince.computeIfAbsent(port, p -> Instant.now());
                    Instant start = lockedNoCarrierSince.get(port);
                    long seconds = Duration.between(start, Instant.now()).getSeconds();

                    // 冷卻判斷
                    Instant lastTried = lastUnlockTriedAt.get(port);
                    boolean inCooldown = lastTried != null &&
                            Duration.between(lastTried, Instant.now()).getSeconds() < cooldownSeconds;

                    if (seconds >= autoUnlockAfterSeconds && !inCooldown) {
                        log.warn("[ZIP-AUTO-UNLOCK] 觸發：port={}, locked={}, status={}, carrierId='{}', 持續={}s",
                                port, st.lockFlag, st.status, st.carrierId, seconds);
                        try {
                            Root<PortLockUnlockSecondaryBody> r = zip.sendPortLockUnlock(target, port, /*cmd*/2);
                            boolean ok = r != null
                                    && r.getBody() != null
                                    && r.getBody().getResultInfos() != null
                                    && !r.getBody().getResultInfos().isEmpty()
                                    && (r.getBody().getResultInfos().get(0).getResult() == 0);

                            lastUnlockTriedAt.put(port, Instant.now());

                            if (!ok) {
                                String code = (r == null || r.getBody() == null
                                        || r.getBody().getResultInfos() == null
                                        || r.getBody().getResultInfos().isEmpty())
                                        ? "NO_RESULT"
                                        : ("ZIP_RESULT=" + r.getBody().getResultInfos().get(0).getResult());
                                log.warn("[ZIP-AUTO-UNLOCK] 解除失敗：port={}, {}", port, code);
                                // 失敗就保留 lockedNoCarrierSince，等待冷卻後再嘗試
                            } else {
                                // 成功後可重查一次確認（非必要）
                                try {
                                    Root<StatusQuerySecondaryBody> check = zip.queryPorts(target, port);
                                    PortState st2 = parsePortState(check, port);
                                    boolean unlocked = (st2 != null) && !st2.locked;
                                    log.info("[ZIP-AUTO-UNLOCK] 成功：port={}, unlocked={}", port, unlocked);
                                } catch (Exception ex2) {
                                    //log.debug("[ZIP-AUTO-UNLOCK] 成功但驗證略過：{}", ex2.getMessage());
                                }
                                // 成功：清除觀測起點，等待下一輪狀態
                                lockedNoCarrierSince.remove(port);
                            }
                        } catch (Exception ex) {
                            lastUnlockTriedAt.put(port, Instant.now());
                            log.warn("[ZIP-AUTO-UNLOCK] 呼叫失敗：port={}, err={}", port, ex.getMessage(), ex);
                        }
                    }
                } else {
                    // 非「鎖定且無載具」→ 重置計時
                    lockedNoCarrierSince.remove(port);
                }

            } catch (Exception ex) {
                //log.debug("[ZIP-AUTO-UNLOCK] 查詢失敗：port={}, err={}", port, ex.getMessage());
                // 查不到狀態時，不改變當前計時，避免抖動；也可選擇重置
            }
        }
    }

    /**
     * 解析 ZIP 狀態回傳：
     * - 依你先前 verifyZipUnlocked 的邏輯：Type=4 的 StatusInfo，Message[1] 為 lockFlag
     * - 推定 Message[0] 可能為 carrierId（若不同，請改成你真實欄位）
     */
    private PortState parsePortState(Root<StatusQuerySecondaryBody> resp, String port) {
        if (resp == null || resp.getBody() == null || resp.getBody().getStatusInfos() == null) return null;
        for (StatusQuerySecondaryBody.StatusInfo s : resp.getBody().getStatusInfos()) {
            if (s == null || s.getType() != 4) continue;
            if (!port.equalsIgnoreCase(String.valueOf(s.getName()))) continue;

            List<?> msg = s.getMessage();
            String carrierId = (msg != null && msg.size() >= 1 && msg.get(0) != null)
                    ? String.valueOf(msg.get(0)).trim() : "";
            String lockFlag = (msg != null && msg.size() >= 2 && msg.get(1) != null)
                    ? String.valueOf(msg.get(1)).trim() : "";
            int status = s.getStatus(); // 51=鎖定

            boolean locked = "1".equals(lockFlag);
            return new PortState(carrierId, lockFlag, status, locked);
        }
        return null;
    }

    private record PortState(String carrierId, String lockFlag, int status, boolean locked) {}
}
