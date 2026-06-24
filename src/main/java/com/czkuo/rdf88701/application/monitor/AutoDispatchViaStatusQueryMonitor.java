package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.DispatchOrder.DispatchOrderSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQuerySecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import com.czkuo.rdf88701.domain.repository.SiteBidirRouteRepository;
import com.czkuo.rdf88701.infra.entity.SiteBidirRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AutoDispatchViaStatusQueryMonitor
 * <p>
 * 不用 DB，直接以 ZIP 的 StatusQuery(Type=3: 儲格狀態) 掃描庫位，
 * 只要 Message[0] 有載具 ID，就視為【在架】，即加入派貨清單並呼叫 DispatchOrder。
 * <p>
 * 規則：
 * - 每 30 秒跑一次（首次延遲 3 秒）
 * - 一輪最多派 MAX_PER_CYCLE 筆
 * - 為避免重複派，在記憶體做冷卻：同一載具 N 分鐘內不重派
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoDispatchViaStatusQueryMonitor {

    // ===== 硬編常數：需要就改這裡 =====
    private static final long INITIAL_DELAY_MS = 3_000L;   // 首次延遲
    private static final long FIXED_DELAY_MS = 30_000L;  // 間隔 30 秒
    private static final int MAX_PER_CYCLE = 1;        // 每輪最多派幾個
    // Port 名稱常數
    private static final String PORT_STK01 = "STK01";
    private static final String PORT_STK02 = "STK02";
    private static final String PORT_SW = "SW";
    private static final String PORT_REJECT = "REJECT";

    // 線性站點（靠近 Transfer 的在前面；此處保留但流程改為依 activeTarget 決策）
    private static final String SITE_15 = "Site#15";
    private static final String SITE_16 = "Site#16";

    @Value("${app.transfer.TR6.pair-code:SITE15_16}")
    private String pairCode; // site_bidir_route 的 pair_code

    // 同一載具的派貨冷卻時間，避免一直重派（例如 5 分鐘）
    private static final Duration COOLDOWN = Duration.ofMinutes(5);

    private final SiteBidirRouteRepository siteBidirRouteRepository;
    private final ZipStockerCommandService zipCommandService;

    // 最近派貨時間（記憶體內），key=載具ID
    private final Map<String, Instant> lastDispatchedAt = new ConcurrentHashMap<>();

    // ========== Port 選擇模式 ==========
    /**
     * STK01 / STK02 / AUTO
     * - STK01 ：固定用 STK01
     * - STK02 ：固定用 STK02
     * - SW    ：固定用 SW
     * - REJECT：固定用 REJECT
     * - AUTO  ：兩個出料口輪流
     */
    @Value("${zip.monitor.stk-port:AUTO}")
    private String stkPortMode;

    private final AtomicInteger rr = new AtomicInteger(0);

    /**
     * 依設定挑選當輪要用的 Port。
     */
    private String pickPort() {
        String mode = (stkPortMode == null ? PORT_STK02 : stkPortMode).trim().toUpperCase();
        switch (mode) {
            case PORT_STK01:
            case PORT_STK02:
            case PORT_SW:
            case PORT_REJECT:
                return mode;
            case "AUTO":
            default:
                // 輪流：偶數用 STK01，奇數用 STK02
                return (rr.getAndIncrement() & 1) == 0 ? PORT_STK01 : PORT_STK02;
        }
    }

    // 若要啟用排程，解除註解
    //@Scheduled(fixedDelay = FIXED_DELAY_MS, initialDelay = INITIAL_DELAY_MS)
    public void scanAndDispatch() {
        try {
            doWork();
        } catch (Exception ex) {
            log.error("[AutoDispatchSQ] 例外：{}", ex.getMessage(), ex);
        }
    }

    private void doWork() {
        // 1) 呼叫 ZIP：StatusQuery(Type=3, all slots)
        Root<StatusQuerySecondaryBody> resp = zipCommandService.queryAllSlots(ZipTarget.ZIPA);
        if (resp == null || resp.getBody() == null || resp.getBody().getStatusInfos() == null) {
            log.warn("[AutoDispatchSQ] StatusQuery 回覆為空");
            return;
        }

        // 2) 從回覆過濾出「有載具 ID 的儲格」
        List<String> candidates = new ArrayList<>();
        for (StatusQuerySecondaryBody.StatusInfo s : resp.getBody().getStatusInfos()) {
            if (s.getType() != 3) continue;                         // 只要儲格
            List<String> msg = s.getMessage();
            if (msg == null || msg.isEmpty()) continue;
            String carrierId = msg.get(1);                           // Message[0] = 載具ID（有物品才帶）
            if (carrierId == null || carrierId.isBlank() || carrierId.contains("cover"))
                continue;
            if ("TY00099VM".equals(carrierId) || "TY00098VM".equals(carrierId) || "TY00089VM".equals(carrierId) || "TY00087VM".equals(carrierId))
                continue;

            // 只派啟用中的儲格（41=啟用）
            if (s.getStatus() != 41) continue;

            // 冷卻檢查：近期剛派過就跳過
            if (isInCooldown(carrierId)) {
                //log.debug("[AutoDispatchSQ] {} 冷卻中，略過", carrierId);
                continue;
            }
            candidates.add(carrierId);
        }

        if (candidates.isEmpty()) {
            //log.debug("[AutoDispatchSQ] 沒有可派的載具");
            return;
        }

        // 一輪最多派 N 筆
        if (candidates.size() > MAX_PER_CYCLE) {
            candidates = candidates.subList(0, MAX_PER_CYCLE);
        }

        // 3) 選擇本輪出料口
        String stkPort = pickPort();

        // 3.1) Port 口檢查
        if (stkPort.equals(PORT_SW)) {
            String activeTarget = siteBidirRouteRepository.findAll().stream()
                    .filter(r -> pairCode.equalsIgnoreCase(r.getPairCode()))
                    .map(SiteBidirRoute::getActiveTarget)
                    .findFirst()
                    .orElse(null);

            if (activeTarget == null || (!activeTarget.equals(SITE_15) && !activeTarget.equals(SITE_16))) {
                //log.debug("[AutoDispatchSQ] 無有效 activeTarget（pairCode={}），略過", pairCode);
                return;
            }

            if (!activeTarget.equals(SITE_15)) {
                //log.debug("[AutoDispatchSQ] 站點不對 暫時禁止入 WIP ，略過");
                return;
            }
        }

        // 4) 發 ZIP：DispatchOrder（一次丟多個 Magazines）
        log.info("[AutoDispatchSQ] 發送 DispatchOrder：stkPort={}, magazines={}", stkPort, candidates);
        Root<DispatchOrderSecondaryBody> d = zipCommandService.sendDispatchOrder(ZipTarget.ZIPA, candidates, stkPort);

        // 5) 逐筆處理回覆結果（結果依序對應）
        List<DispatchOrderSecondaryBody.ResultInfo> results =
                (d != null && d.getBody() != null) ? d.getBody().getResultInfos() : Collections.emptyList();

        for (int i = 0; i < candidates.size(); i++) {
            String carrierId = candidates.get(i);
            DispatchOrderSecondaryBody.ResultInfo r = (i < results.size()) ? results.get(i) : null;

            if (r != null && r.getResult() == 0) {
                log.info("[AutoDispatchSQ] 派貨成功：{} (port={})", carrierId, stkPort);
                lastDispatchedAt.put(carrierId, Instant.now());
            } else {
                String reason = (r == null) ? "NO_RESULT" : ("ZIP_RESULT=" + r.getResult());
                log.warn("[AutoDispatchSQ] 派貨失敗：{}，原因：{} (port={})", carrierId, reason, stkPort);
                // 失敗不寫入冷卻，讓下輪可再嘗試（你也可選擇寫入短冷卻避免猛打）
            }
        }

        // 6) 清掉過舊的冷卻記錄（避免 map 無限長大）
        cleanupCooldowns();
    }

    private boolean isInCooldown(String carrierId) {
        Instant at = lastDispatchedAt.get(carrierId);
        return at != null && Instant.now().isBefore(at.plus(COOLDOWN));
    }

    private void cleanupCooldowns() {
        Instant now = Instant.now();
        lastDispatchedAt.entrySet().removeIf(e -> now.isAfter(e.getValue().plus(COOLDOWN.multipliedBy(2))));
    }
}
