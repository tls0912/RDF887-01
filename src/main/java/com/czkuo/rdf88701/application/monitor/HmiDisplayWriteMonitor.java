package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.domain.repository.HmiDisplayTaskRepository;
import com.czkuo.rdf88701.infra.entity.HmiDisplayTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HMI 訊息寫入監控
 * <p>
 * 規格（依你提供的表）：
 *   - 訊息：W00C0 起，共 25W，每 W 放 2 個字元 → 50 chars 固定長度
 *   - 索引：W00DC 起，這裡簡化用 Int32（2W）存十進位數值：讀 → +1 → 寫回（溢位回 1）
 *   - 裝置：PLC-Main（可改常數）
 * <p>
 * 風險/說明：
 *   - 這版用 JVM 內的 busy flag 防重入；如果未來多實例/叢集，建議加「DB claim（狀態轉 SENDING）」
 *     來避免同一筆任務被多處同時處理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HmiDisplayWriteMonitor {

    // ====== 直寫常數（需要改就改這裡） ======
    private static final String PLC_DEVICE_NAME   = "PLC-Main";
    private static final String MSG_START_ADDR    = "W00C0"; // 0x00C0
    private static final int    MSG_WORDS         = 25;      // 25W × 2 chars = 50 chars
    private static final int    MSG_LEN_CHARS     = MSG_WORDS * 2;

    private static final String INDEX_ADDR        = "W00DF"; // 0x00DD （Int32 1W）
    // private static final int    INDEX_MAX         = 99_999_999; // 溢位重設到 1
    private static final int    INDEX_MAX         = 9_999; // 溢位重設到 1

    private final HmiDisplayTaskRepository repo;
    private final PlcAccessService plc;

    /**
     * 單 JVM 防重入；多實例要用 DB 端的 claim 機制避免併發
     */
    private final AtomicBoolean busy = new AtomicBoolean(false);

    /**
     * 300ms 跑一次；一次只處理一筆（避免 index 競爭）
     */
    @Scheduled(fixedDelay = 500, initialDelay = 1200)
    public void tick() {
        if (!busy.compareAndSet(false, true)) return;
        try {
            processOne();
        } catch (Exception ex) {
            log.error("[HMI] 未預期例外：{}", ex.getMessage(), ex);
        } finally {
            busy.set(false);
        }
    }

    private void processOne() {
        // 1) 先撈出所有，挑最舊的 PENDING 一筆（你的 Repository 目前只有 findAll，就在記憶體篩）
        List<HmiDisplayTask> all = repo.findAll();
        HmiDisplayTask task = all.stream()
                .filter(t -> "PENDING".equalsIgnoreCase(safeStr(t.getStatus())))
                .min(Comparator.comparing(HmiDisplayTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        if (task == null) return;

        // 單機版：直接處理（多實例要加「claim」避免重複）
        String raw = safeStr(task.getMsgEn());
        String payload = toFixedAscii(raw, MSG_LEN_CHARS).replace("維","维");

        try {
            // 2) 先寫入訊息（固定 50 chars）
            plc.writeString(PLC_DEVICE_NAME, MSG_START_ADDR, payload);

            // 3) 讀 index → +1（<=0 或 >MAX 時回 1）→ 寫回
            int cur = plc.readInt32(PLC_DEVICE_NAME, INDEX_ADDR);
            int next = nextIndex(cur);
            plc.writeInt32(PLC_DEVICE_NAME, INDEX_ADDR, next);

            // 4) 成功：標記 SENT
            task.setStatus("SENT");
            task.setSentAt(LocalDateTime.now());
            repo.update(task);

            log.info("[HMI] SENT id={} tid={} index {} -> {} msg='{}'",
                    task.getId(), task.getTid(), cur, next, printable(payload));

        } catch (Exception ex) {
            // 5) 失敗：標記 FAILED、累計 attempts、帶上 lastError（截斷）
            task.setStatus("FAILED");
            task.setAttempts(task.getAttempts() == null ? 1 : task.getAttempts() + 1);
            String err = ex.getClass().getSimpleName() + ": " + safeStr(ex.getMessage());
            task.setLastError(err.length() > 480 ? err.substring(0, 480) : err);
            repo.update(task);

            log.error("[HMI] FAILED id={} tid={} err={}", task.getId(), task.getTid(), err, ex);
        }
    }

    // ==== helpers ====

    private static int nextIndex(int current) {
        long n = (long) current + 1;
        if (n <= 0 || n > INDEX_MAX) n = 1;
        return (int) n;
    }

    /**
     * 固定長度 ASCII：非 ASCII → '?'; 超過截斷，不足補空白
     */
    private static String toFixedAscii(String s, int len) {
        if (s == null) s = "";
        int lenTemp = 0;
        StringBuilder out = new StringBuilder(len);
        for (int i = 0; i < s.length() && out.length() < len; i++) {
            char c = s.charAt(i);
            //out.append(c <= 0x7F ? c : '?');
            out.append(c);
            lenTemp = lenTemp + c <= 0x7F ? 1 : 2;
        }
        while (out.length() < len) out.append(' ');
        return out.toString();
    }

    private static String safeStr(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String printable(String s) {
        return s == null ? "" : s.replaceAll("\\p{Cntrl}", "?");
    }
}
