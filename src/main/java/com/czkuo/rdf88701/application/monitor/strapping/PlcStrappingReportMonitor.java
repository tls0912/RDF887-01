package com.czkuo.rdf88701.application.monitor.strapping;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.repository.StrappingLogRepository;
import com.czkuo.rdf88701.infra.entity.StrappingLog;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class PlcStrappingReportMonitor {

    private static final String PLC = "PLC-Packer";

    /**
     * PLC 區域設定
     */
    private static final String DATA_BASE_ADDR = "W2000"; // buffer 起始位址
    private static final int GROUP_SIZE  = 30;            // 每筆紀錄 30 words
    private static final int DATA_GROUPS = 10;            // 總共 10 筆 buffer
    private static final int DATA_WORDS  = GROUP_SIZE * DATA_GROUPS; // 300 words
    private static final String INDEX_ADDR = "W212F";     // index 位址（0..65535 ring）

    private final PlcAccessService plc;
    private final StrappingLogRepository repo;

    private final AtomicBoolean busy = new AtomicBoolean(false);

    /**
     * 我們自己的 epoch + lastIndex（用 DB 初始化）
     */
    private volatile int lastIndex = -1; // unknown
    private volatile int lastEpoch = 0;

    /**
     * PLC 是否已成功做過一次 startup 對齊/掃描
     */
    private volatile boolean plcAligned = false;

    /**
     * init 失敗時避免每 500ms 狂刷 log：做簡單節流
     */
    private volatile long nextInitRetryAtMs = 0L;
    private static final long INIT_RETRY_BACKOFF_MS = 5_000;

    @PostConstruct
    public void init() {
        // 只做 DB 游標初始化；不要在 PostConstruct 硬打 PLC
        repo.findLastCursor().ifPresentOrElse(cursor -> {
            this.lastEpoch = cursor.seqEpoch();
            this.lastIndex = cursor.seqIndex();
            log.info("[STRAP] init from DB cursor: epoch={}, lastIndex={}", lastEpoch, lastIndex);
        }, () -> log.info("[STRAP] init: DB empty, cursor not set"));

        // PLC 對齊延後到 tick 內做（可重試）
        this.plcAligned = false;
        this.nextInitRetryAtMs = 0L;
    }

    @Scheduled(fixedDelay = 500, initialDelay = 2000)
    public void tick() {
        if (!busy.compareAndSet(false, true)) return;
        try {
            // 先確保 PLC 已對齊（成功一次即可）
            if (!plcAligned) {
                tryAlignWithPlcOnce();
                return; // 對齊完成的那一輪就先結束，下一輪再走正常流程
            }

            processOnce();
        } catch (Exception e) {
            log.error("[STRAP] monitor error", e);
        } finally {
            busy.set(false);
        }
    }

    /**
     * 嘗試做一次 PLC 對齊：
     * - 成功：startup 掃 window（由舊到新，空跳，有值 upsert），並把 lastIndex 對齊到 currIndex
     * - 失敗：不改動 cursor，後續 tick 會以 backoff 重試
     */
    private void tryAlignWithPlcOnce() {
        long now = System.currentTimeMillis();
        if (now < nextInitRetryAtMs) return;

        try {
            int currIndex = readUnsigned16(plc.readInt16(PLC, INDEX_ADDR));

            // DB 沒資料時：lastIndex 先設 currIndex（但仍掃 window 補漏）
            if (lastIndex < 0) {
                lastIndex = currIndex;
            }

            // 啟動掃一輪 window（由最舊到最新）
            scanWindowOldToNew(currIndex, /*windowCrossWrap*/ false);

            // 對齊游標到 PLC
            lastIndex = currIndex;
            plcAligned = true;

            log.info("[STRAP] PLC aligned. epoch={}, lastIndex={}", lastEpoch, lastIndex);
        } catch (Exception e) {
            // 失敗不動游標，避免亂跳 epoch/index
            nextInitRetryAtMs = now + INIT_RETRY_BACKOFF_MS;
            log.warn("[STRAP] PLC align failed, will retry after {} ms. reason={}",
                    INIT_RETRY_BACKOFF_MS, e.toString());
        }
    }

    protected void processOnce() {
        int currIndex = readUnsigned16(plc.readInt16(PLC, INDEX_ADDR));
        if (lastIndex < 0) {
            // 理論上不會發生（因為 plcAligned 代表已對齊），但留保險
            lastIndex = currIndex;
        }

        if (currIndex == lastIndex) return;

        // epoch 偵測：PLC index 回到小值（重滾/溢位/重置）
        boolean wrapped = currIndex < lastIndex;
        if (wrapped) {
            lastEpoch += 1;
            log.warn("[STRAP] seq_index wrapped: lastIndex={} -> currIndex={}, epoch++ => {}",
                    lastIndex, currIndex, lastEpoch);
        }

        // 每次都掃「整個 10 筆 window」，由舊到新；不依賴 diff
        scanWindowOldToNew(currIndex, wrapped);

        lastIndex = currIndex;
    }

    /**
     * 從最舊（offset=10 window 的第一筆）掃到最新（currIndex 對應那筆）
     * - 空資料跳過
     * - 有值才 upsert (exists->insert)
     * - epoch 分配：若本次 wrapped=true，且 seq > currIndex，視為上一輪 epoch
     */
    private void scanWindowOldToNew(int currIndex, boolean wrappedThisTick) {
        byte[] block;
        try {
            block = plc.readBytes(PLC, DATA_BASE_ADDR, DATA_WORDS * 2);
        } catch (Exception e) {
            // PLC readBytes 失敗：不要改 cursor，讓下一輪再來
            throw e;
        }

        if (block == null || block.length < DATA_WORDS * 2) return;

        int[] words = PlcDataCodec.bytesToWords(block);

        // 最舊到最新：k = 9..0 (9 最舊，0 最新)
        for (int k = DATA_GROUPS - 1; k >= 0; k--) {
            int seq = unsignedMod(currIndex - k, 65536);

            int groupNo = calcGroupNo(seq); // ring slot
            int base = groupNo * GROUP_SIZE;

            // 先讀關鍵欄位判斷「空」
            String productId = decodeAscii50(words, base, 25);
            int resultWord   = words[base + 25] & 0xFFFF;
            int strapPos     = words[base + 26] & 0xFFFF;
            int yymm         = words[base + 27] & 0xFFFF;
            int ddhh         = words[base + 28] & 0xFFFF;
            int mmss         = words[base + 29] & 0xFFFF;

            if (isEmptyRecord(productId, resultWord, strapPos, yymm, ddhh, mmss)) {
                continue;
            }

            LocalDateTime eventTime = parsePlcTime(yymm, ddhh, mmss);
//            byte result = (byte) ((resultWord == 2) ? 2 : 1);
            byte result = (byte) (resultWord == 2 ? 2 : resultWord == 1 ? 1 : 3);

            // epoch：跨 wrap 時，seq > currIndex 視為上一輪
            int epoch = lastEpoch;
            if (wrappedThisTick && seq > currIndex) {
                epoch = lastEpoch - 1;
            }
            if (epoch < 0) epoch = 0;

            byte machinePos = (byte) strapPos; // 你的資料：strapPos == 機台 1/2/3

            // upsert（依 uk_pos_epoch_idx）
            // 改用event_time當KEY
            if (!productId.isEmpty() && !repo.existsByMachinePosEpochAndEventTime(machinePos, epoch, eventTime)) {
                StrappingLog row = new StrappingLog();
                row.setMachinePos(machinePos);
                row.setSeqEpoch(epoch);
                row.setSeqIndex(seq);
                row.setProductId(productId);
                row.setResult(result);
                row.setStrappingPos((byte) strapPos);
                row.setEventTime(eventTime);

                repo.save(row);

                log.info("[STRAP] insert epoch={} seq={} pos={} result={} pid='{}' t={}",
                        epoch, seq, strapPos, result, productId, eventTime);
            }
        }
    }

    /**
     * ring slot 計算：
     * 常見是 seq % 10
     * 若你 PLC 是 (seq - 1) % 10，把這行改掉即可。
     */
    private int calcGroupNo(int seqIndex) {
        return unsignedMod(seqIndex, DATA_GROUPS);
        // return unsignedMod(seqIndex - 1, DATA_GROUPS); // ← 若 PLC 用 1-based index slot
    }

    private boolean isEmptyRecord(String productId, int resultWord, int strapPos, int yymm, int ddhh, int mmss) {
        // productId 空、result/pos/time 全 0 => 當作空
        if (productId != null && !productId.isBlank()) return false;
        return resultWord == 0 && strapPos == 0 && yymm == 0 && ddhh == 0 && mmss == 0;
    }

    // === helpers ===

    private static int readUnsigned16(int v) {
        return v & 0xFFFF;
    }

    private static int unsignedMod(int v, int mod) {
        int r = v % mod;
        return (r < 0) ? (r + mod) : r;
    }

    private String decodeAscii50(int[] words, int offset, int countWords) {
        StringBuilder sb = new StringBuilder(countWords * 2);
        for (int i = 0; i < countWords; i++) {
            int w = words[offset + i] & 0xFFFF;
            char hi = (char) ((w >> 8) & 0xFF);
            char lo = (char) (w & 0xFF);
            if (lo != 0) sb.append(lo);
            if (hi != 0) sb.append(hi);
        }
        return sb.toString().trim();
    }

    private static LocalDateTime parsePlcTime(int yymm, int ddhh, int mmss) {
        int yy = bcdToInt((yymm >> 8) & 0xFF);  // High byte → Year
        int mm = bcdToInt(yymm & 0xFF);         // Low byte  → Month
        int dd = bcdToInt((ddhh >> 8) & 0xFF);
        int hh = bcdToInt(ddhh & 0xFF);
        int mi = bcdToInt((mmss >> 8) & 0xFF);
        int ss = bcdToInt(mmss & 0xFF);

        int year = 2000 + yy;
        try {
            return LocalDateTime.of(year, mm, dd, hh, mi, ss);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /**
     * BCD → int (ex: 0x24 → 24)
     */
    private static int bcdToInt(int bcd) {
        int hi = (bcd >> 4) & 0xF;
        int lo = bcd & 0xF;
        return hi * 10 + lo;
    }
}
