package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.domain.repository.AlarmItemLogRepository;
import com.czkuo.rdf88701.domain.repository.AlarmItemRepository;
import com.czkuo.rdf88701.infra.entity.AlarmItem;
import com.czkuo.rdf88701.infra.entity.AlarmItemLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WARNING 批次送 PLC 監控（使用 PlcAccessService）
 *
 * - 從 DB 取 want_plc_trigger=1 的資料（FOR UPDATE SKIP LOCKED）
 * - 一次最多 15 筆 → 寫入 W00E0..W00EE
 * - W00EF 寫 INDEX（每次 = 上次 + 1；預設 BCD 4位，可改用 16-bit 十進位）
 * - 寫成功才清 want_plc_trigger，並針對本批每筆寫一條 alarm_item_log='PLC_OFF'
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarningPlcWriteMonitor {

    private static final String PLC_DEVICE_NAME = "PLC-Main";
    private static final String DATA_START_ADDR = "W00E0"; // W00E0..W00EF 共 16W
    private static final String INDEX_ADDR      = "W00EF"; // 最後 1W 放 INDEX
    private static final int    SLOTS           = 15;      // 前 15W 放 global_code
    private static final int    INDEX_MAX         = 9_999; // 溢位重設到 1

    /** Omron/FINS 常見大端；若你的底層已有處理可忽略這個設定 */
    private static final boolean BIG_ENDIAN = false;

    private final AlarmItemRepository repo;
    private final AlarmItemLogRepository logRepo;
    private final PlcAccessService plc;

    private final AtomicBoolean busy = new AtomicBoolean(false);


    @Scheduled(fixedDelay = 300, initialDelay = 1200)
    public void tick() {
        if (!busy.compareAndSet(false, true)) return;
        try { processOneBatch(); }
        catch (Exception e) { log.error("[WARN→PLC] unexpected error: {}", e.getMessage(), e); }
        finally { busy.set(false); }
    }

    /** 一次處理 <=15 筆：寫 16W（包含 INDEX），成功才清旗標並記 log */
    @Transactional
    protected void processOneBatch() {
        List<AlarmItem> batch = repo.claimPendingForPlc(SLOTS);
        if (batch.isEmpty()) return;

        byte[] buf = new byte[16 * 2]; // 16 words × 2 bytes
        List<Long> ids = new ArrayList<>(batch.size());

        // W00E0..W00EE：填 local_code（不足補 0）
        for (int i = 0; i < SLOTS; i++) {
            if (i < batch.size()) {
                AlarmItem it = batch.get(i);
                ids.add(it.getId());
                putWord(buf, i, it.getLocalCode() & 0xFFFF);
            } else {
                putWord(buf, i, 0);
            }
        }

        // INDEX：讀 W00EF → +1（1..65535）→ 寫進區塊最後 1W
        int cur = readWordU16(PLC_DEVICE_NAME, INDEX_ADDR);
        int next = nextIndex(cur);
        putWord(buf, 15, next);

        // 一次寫入 W00E0 起連續 16W
        plc.writeBytes(PLC_DEVICE_NAME, DATA_START_ADDR, buf);

        // 清旗標
        repo.clearWantPlcByIds(ids);

        // 每筆寫一條 alarm_item_log='PLC_OFF'
        List<AlarmItemLog> logs = new ArrayList<>(batch.size());
        for (AlarmItem it : batch) {
            AlarmItemLog row = new AlarmItemLog();
            row.setItemId(it.getId());
            row.setGlobalCode(it.getGlobalCode());
            row.setTitleZh(it.getTitleZh());
            row.setTitleEn(it.getTitleEn());
            row.setEventType("PLC_OFF");
            logs.add(row);
        }
        if (!logs.isEmpty()) logRepo.saveBatch(logs);

        log.info("[WARN→PLC] SENT size={} index {} -> {}", batch.size(), cur & 0xFFFF, next);
    }

    // ===== helpers =====
    private static int nextIndex(int current) {
        long n = (long) current + 1;
        if (n <= 0 || n > INDEX_MAX) n = 1;
        return (int) n;
    }

    private static void putWord(byte[] buf, int wordIndex, int value) {
        int off = wordIndex * 2;
        if (BIG_ENDIAN) {
            buf[off]     = (byte) ((value >>> 8) & 0xFF);
            buf[off + 1] = (byte) ( value        & 0xFF);
        } else {
            buf[off]     = (byte) ( value        & 0xFF);
            buf[off + 1] = (byte) ((value >>> 8) & 0xFF);
        }
    }

    private int readWordU16(String device, String addr) {
        byte[] b = plc.readBytes(device, addr, 2);
        if (b == null || b.length < 2) return 0;
        return BIG_ENDIAN
                ? ((b[0] & 0xFF) << 8) | (b[1] & 0xFF)
                : ((b[1] & 0xFF) << 8) | (b[0] & 0xFF);
    }
}
