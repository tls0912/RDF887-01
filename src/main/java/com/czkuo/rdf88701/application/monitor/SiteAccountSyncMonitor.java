package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.domain.plc.state.site.SiteCommandStatus;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcSiteWordWriter;
import com.czkuo.rdf88701.infra.cache.SiteCommandCache;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


/**
 * SiteAccountSyncMonitor
 * ------------------------------------------------------------
 * 功能：
 *   依據 DB 真相（location_tracking 與 container_main.alias_code），將「產品序號」
 *   同步到 PLC 的 Site ASCII50 區（25 words）。本版要求「不要寫空白，要寫 0x00」，
 *   因此所有「清空」與「不足補滿」都以 0x00 (NUL) 補足。
 *
 * 流程：
 *   1) 從 LocationTrackingRepository.findContainerAtLocationName("Site#X") 取該站點上的 container_main_id
 *   2) 用 ContainerMainRepository.findById(id) 取 alias_code
 *   3) 將 alias_code 正規化為 50 字（不含控制碼；非 ASCII ⇒ '?'），不足以 0x00 補滿
 *   4) 與目前快取/PLC 狀態比對，僅在有差異時寫入（writeAscii50Only）
 *
 * 重要策略與參數：
 *   - clearWhenEmpty：站點無容器或序號為空時是否「清空為 50 個 0x00」
 *   - compareByTrim：原為空白補滿才有意義；本版改用 0x00 補滿，trim() 對 0x00 無效，
 *                    實務上等於「嚴格比較」。保留此旗標以維持舊設定檔相容，但預期無作用。
 *   - targetsCsv / scanMaxSiteId：控制掃描目標站點
 *
 * 備註：
 *   - 以 0x00 補滿時，請確認 PLC 端的字元/Word 寫入不會被上層自動轉成空白或被截斷。
 *   - 若 siteCommandCache（可為 null）無資料，視為 50 個 0x00，藉此觸發第一次寫入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteAccountSyncMonitor {

    private final ContainerMainRepository containerMainRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final PlcSiteWordWriter plcSiteWordWriter;
    private final SiteCommandCache siteCommandCache; // 可為 null（若未注入，差異判斷以 0x00 當前狀）

    @Value("${monitor.site-account-sync.poll-ms:100}")
    private long pollMs;

    /**
     * 無容器或序號為空時是否清空為 50 個 0x00。
     * true：desired = 50*NUL
     * false：desired = ""（空字串），將由 shouldSkip() 與實際 current 判斷是否需要寫入
     */
    @Value("${monitor.site-account-sync.clear-when-empty:true}")
    private boolean clearWhenEmpty;

    /**
     * 舊版為空白補滿才有意義；本版全採 0x00 補滿，trim() 對 0x00 不生效 ⇒ 形同嚴格比較。
     * 保留旗標只為相容舊設定，實際上 equals 與 trim().equals() 結果相同。
     */
    @Value("${monitor.site-account-sync.compare-by-trim:true}")
    private boolean compareByTrim;

    /** 掃描站號上限（1..N）；若設定了 targets 則忽略此值 */
    @Value("${monitor.site-account-sync.scan-max-site-id:64}")
    private int scanMaxSiteId;

    /** 逗號分隔站號，例如 "1,2,5"；空字串=不限制（用 1..scanMaxSiteId） */
    @Value("${monitor.site-account-sync.targets:}")
    private String targetsCsv;

    /** 記錄曾經寫入過的 ASCII50（作為掃描補集與可選的差異參考） */
    private final Map<Integer, String> lastWrittenAscii50 = new ConcurrentHashMap<>();

    /** 共用常數：50 個 0x00 字元的字串（NUL × 50） */
    private static final String FILL_50_ZERO = makeZero50String();

    /**
     * 週期性同步（上一輪完成後延遲 pollMs 再跑下一輪）
     */
    @Scheduled(fixedDelayString = "${monitor.site-account-sync.poll-ms:100}")
    public void sync() {
        try {
            final Set<Integer> targets = resolveTargets();
            if (targets.isEmpty()) return;

            int writes = 0, skips = 0;

            for (Integer siteId : targets) {

                // ---- 1) 取 DB 真相：站點上的 container_main_id ----
                final String locationName = "Site#" + siteId;
                Optional<Long> optContainer = locationTrackingRepository.findContainerAtLocationName(locationName);

                // ---- 2) 取 alias_code（可能為 null/空字串） ----
                String alias = optContainer
                        .flatMap(containerMainRepository::findById)
                        .map(ContainerMain::getAliasCode)
                        .orElse(null); // 注意：此處回 null，交由 toFixed50() 處理

                // ---- 3) 產生欲寫入值：固定 50 字，不足以 0x00 補滿；無值時依設定清 0x00 × 50 ----
                String desired50 = toFixed50(alias, clearWhenEmpty);

                // ---- 4) 取目前快取/PLC 狀態：若沒有，視為 0x00 × 50 ----
                //      * 使用 Optional 鏈，任何一步 null 都會回傳 FILL_50_ZERO，避免 NPE 與不必要 skip
                String current = Optional.ofNullable(siteCommandCache)
                        .map(c -> c.getLatest(siteId))
                        .map(SiteCommandStatus::getProductId)
                        .orElse(FILL_50_ZERO);

                // ---- 5) 差異判斷：以 0x00 為補字，因此基本上需要嚴格字串相等才 skip ----
                if (shouldSkip(current, desired50)) {
                    skips++;
                    continue;
                }

                // ---- 6) 寫入 PLC 的 ASCII50（僅寫 base+6 的 25 words）----
                plcSiteWordWriter.writeAscii50Only(siteId, desired50);
                lastWrittenAscii50.put(siteId, desired50);
                writes++;
            }

            // if (writes > 0 || log.isDebugEnabled()) {
            if (writes > 0) {
                log.info("[SiteAcctSync] done: writes={}, skips={}, targets={} (pollMs={})",
                        writes, skips, targets.size(), pollMs);
            }
        } catch (Exception e) {
            log.warn("[SiteAcctSync] ❌ failure: {}", e.toString(), e);
        }
    }

    // ------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------

    /**
     * 解析要掃描的站點集合：
     *   - 若 targetsCsv 有值 → 以其為準
     *   - 否則掃描 [1..scanMaxSiteId]
     *   - 並將曾經寫入過的站點（lastWrittenAscii50.keySet()）納入，避免後續清空漏掃
     */
    private Set<Integer> resolveTargets() {
        if (targetsCsv != null && !targetsCsv.isBlank()) {
            return Arrays.stream(targetsCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try { return Integer.parseInt(s); }
                        catch (NumberFormatException e) { return null; }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        Set<Integer> ids = new LinkedHashSet<>();
        for (int i = 1; i <= Math.max(1, scanMaxSiteId); i++) ids.add(i);
        ids.addAll(lastWrittenAscii50.keySet()); // 將曾寫入的站點也納入
        return ids;
    }

    /**
     * 判斷是否可跳過寫入（current 與 desired50 相同就跳過）
     *
     * 注意：
     *   - 本版以 0x00 作為補字，因此 trim() 對結果沒有影響，嚴格比較即可。
     *   - 若 future 想兼容「某些 PLC 會把 NUL 顯示為空白」的場景，可在此加入等價規則。
     */
    private boolean shouldSkip(String current, String desired50) {
        if (current == null) return false; // 保守：未知現況則不跳過
        if (compareByTrim) {
            // trim() 不會移除 0x00，與 equals() 等效；保留以相容舊設定
            return current.trim().equals(desired50.trim());
        } else {
            return current.equals(desired50);
        }
    }

    /**
     * 將來源字串正規化為固定 50 字：
     *   - 若 src 為 null/空白：
     *       clearEmpty = true  ⇒ 回傳 0x00 × 50（清空）
     *       clearEmpty = false ⇒ 回傳 ""（讓上層用 current 與 desired50 決定是否要覆寫）
     *   - 若有內容：
     *       逐字檢查：
     *         * ASCII 可打印字元 (0x20..0x7E) 保留
     *         * 其它非控制字元以 '?' 代替
     *         * 控制字元直接略過
     *       最後不足 50 以 0x00 補滿
     */
    private String toFixed50(String src, boolean clearEmpty) {
        String s = (src == null) ? "" : src.trim();
        if (s.isEmpty()) {
            return clearEmpty ? FILL_50_ZERO : "";
        }
        StringBuilder out = new StringBuilder(50);
        for (int i = 0; i < s.length() && out.length() < 50; i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c <= 0x7E) {
                // ASCII 可打印字元
                out.append(c);
            } else if (!Character.isISOControl(c)) {
                // 非 ASCII 但非控制字元 → 以 '?' 代替
                out.append('?');
            } // 控制字元（如 \n, \r, \t 等）直接略過
        }
        // 不足補 0x00（NUL）
        while (out.length() < 50) out.append('\0');
        return out.toString();
    }

    /**
     * 產生「50 個 0x00 (NUL)」的常數字串。
     * 使用單獨方法以利 JVM 常駐與單測。
     */
    private static String makeZero50String() {
        char[] buf = new char[50];
        // char 陣列預設值即為 '\0'，此處顯式填入更直觀
        Arrays.fill(buf, '\0');
        return new String(buf);
    }
}
