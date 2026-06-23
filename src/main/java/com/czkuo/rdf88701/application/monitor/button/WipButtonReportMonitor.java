package com.czkuo.rdf88701.application.monitor.button;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.repository.ButtonLogRepository;
import com.czkuo.rdf88701.infra.entity.ButtonLog;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WipButtonReportMonitor
 * -----------------------------------------------------------------------------
 * 功能：
 *   - 週期性讀取「拆併區」PLC Button Report 緩衝區（W2400~W243F）
 *   - 解析 PLC 寫入的：
 *       Button ID / ReturnCode / 時間(YYMM,DDhh,mmss)
 *   - 依照 PLC 的流水號 index 寫入 button_log 資料表（area = WIP）
 *
 * 設計重點：
 *   1) 採「環形 buffer + index」模式，就像 strapping_log 那支一樣
 *   2) 重新啟動時，會先從 DB 取該 area 的最大 seq_index，避免重複寫
 *   3) 若 index 跳動超過 buffer 容量，會警告「可能遺失紀錄」
 *   4) 以 (area, seq_index) 做唯一約束，防止重複寫入
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WipButtonReportMonitor {

    /** ButtonLog.area 欄位，代表來源區域（拆併區） */
    private static final String AREA = "WIP";

    /** 對應 PlcAccessService 設定的 PLC 名稱（如果不同請修改） */
    private static final String PLC = "PLC-Main";

    // -------------------------------------------------------------------------
    // PLC 記憶體配置（可依實際位址微調）
    // -------------------------------------------------------------------------

    /**
     * Button Report buffer 起始位址
     *
     * 依你提供的表：
     *   W2400 Button ID #1
     *   W2401 ReturnCode #1
     *   W2402 Time YYMM #1
     *   W2403 Time DDhh #1
     *   W2404 Time mmss #1
     *   ...
     *   W2428~W242C → 第 9 筆
     *   W243F → Index
     */
    private static final String DATA_BASE_ADDR = "W2400";

    /** 每筆紀錄佔用的 words 數：ButtonID / ReturnCode / YYMM / DDhh / mmss */
    private static final int GROUP_SIZE = 5;

    /** PLC 端預留 9 筆紀錄（Button ID #1 ~ #9） */
    private static final int DATA_GROUPS = 9;

    /** 整個 buffer 需要讀取的 word 數量：5 words * 9 group = 45 words */
    private static final int DATA_WORDS = GROUP_SIZE * DATA_GROUPS;

    /** index 位址（PLC 每寫入一筆就 index+1，並循環使用 buffer） */
    private static final String INDEX_ADDR = "W243F";

    // -------------------------------------------------------------------------
    // DI 注入
    // -------------------------------------------------------------------------

    private final PlcAccessService plc;
    private final ButtonLogRepository repo;

    // -------------------------------------------------------------------------
    // 狀態欄位
    // -------------------------------------------------------------------------

    /**
     * 避免 Scheduler re-entry：
     *   - tick() 還沒執行完時，下一輪排程到來就會被忽略
     */
    private final AtomicBoolean busy = new AtomicBoolean(false);

    /**
     * 上一次處理到的 PLC index
     *   - 第一次啟動時，從 DB 或 PLC 讀取初始值
     *   - 之後每成功處理一批紀錄，就更新為 currIndex
     */
    private volatile int lastIndex = 0;

    // -------------------------------------------------------------------------
    // 初始化：從 DB 或 PLC 取初始 index
    // -------------------------------------------------------------------------

    @PostConstruct
    public void init() {
        // 1) 優先從 DB 找該 area 的最後一筆 seq_index
        Optional<Integer> lastIdxOpt = repo.findLastSeqIndexByArea(AREA);
        if (lastIdxOpt.isPresent()) {
            lastIndex = lastIdxOpt.get();
            log.info("[BTN-{}] init lastIndex from DB = {}", AREA, lastIndex);
        }

        // 2) 如果 DB 還沒資料，就直接對齊 PLC 目前的 index
        // int currIndex = plc.readInt16(PLC, INDEX_ADDR);
        // if (currIndex < 0) {
        //     currIndex = 0;
        // }
        // lastIndex = currIndex;
        // log.info("[BTN-{}] init lastIndex from PLC = {}", AREA, lastIndex);
    }

    // -------------------------------------------------------------------------
    // Scheduler 入口：固定週期呼叫 processOnce()
    // -------------------------------------------------------------------------

    /**
     * 每 500ms 掃描一次拆併區按鈕紀錄。
     * initialDelay 設 2000ms，給系統一點啟動緩衝。
     */
    @Scheduled(fixedDelay = 500, initialDelay = 2000)
    public void tick() {
        // 簡易 re-entry guard
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        try {
            processOnce();
        } catch (Exception e) {
            log.error("[BTN-{}] monitor error", AREA, e);
        } finally {
            busy.set(false);
        }
    }

    // -------------------------------------------------------------------------
    // 核心流程：
    //   1) 讀 index
    //   2) 如有變動 → 讀整個 buffer
    //   3) 計算 diff / 處理環形 buffer
    //   4) 寫入 DB + log
    // -------------------------------------------------------------------------

    protected void processOnce() {
        // 1) 讀取 PLC 現在的 index
        int currIndex = plc.readInt16(PLC, INDEX_ADDR);
        if (currIndex < 0 || currIndex == lastIndex) {
            // <0 → 讀取失敗或 PLC 還沒初始化
            // ==lastIndex → 沒新增紀錄
            return;
        }

        // 2) 讀取整個 Button Report buffer
        //    注意：readBytes 的長度單位是「byte」，所以 words * 2
        byte[] block = plc.readBytes(PLC, DATA_BASE_ADDR, DATA_WORDS * 2);
        if (block == null || block.length < DATA_WORDS * 2) {
            log.warn("[BTN-{}] read block fail, len={}", AREA,
                    (block == null ? -1 : block.length));
            return;
        }
        int[] words = PlcDataCodec.bytesToWords(block);

        // 3) 計算 index 差異（diff）
        int diff = currIndex - lastIndex;
        if (diff < 0) {
            // 代表 index 溢位（16-bit）：例如 65535 → 0
            // 用 +65536 把它轉成正的差值
            diff = (currIndex + 65536) - lastIndex;
        }

        // 若 diff 超過 buffer 容量，代表期間有部份舊紀錄被覆蓋
        if (diff > DATA_GROUPS) {
            log.warn("[BTN-{}] index jump {} (> buffer {}) → 可能遺失部分按鈕紀錄",
                    AREA, diff, DATA_GROUPS);
            // 為了安全，只處理 buffer 裡「最後放得下」的部分
            diff = DATA_GROUPS;
        }

        // 4) 逐一處理「新增加」的 index：
        //    範圍：lastIndex+1 ~ currIndex（包含兩端）
        for (int idx = currIndex - diff + 1; idx <= currIndex; idx++) {
            // 4-1) 計算此 idx 對應到環形 buffer 的第幾組 group
            //
            // 做法：假設 currIndex 對應到「最新一筆」所在 group，
            //       其他較舊的依序往前推。
            //
            // e.g. currIndex=105, DATA_GROUPS=9
            //   idx=105 → groupNo=(105-105)%9=0 → 最新那格
            //   idx=104 → groupNo=(105-104)%9=1 → 倒數第二格
            int groupNo = (currIndex - idx) % DATA_GROUPS;
            if (groupNo < 0) {
                groupNo += DATA_GROUPS;
            }

            // 4-2) 算出此記錄在 words[] 裡的起始 index
            int base = groupNo * GROUP_SIZE;

            // 4-3) 把 5 個 word 拆出來
            int buttonIdWord   = words[base]     & 0xFFFF;
            int returnCodeWord = words[base + 1] & 0xFFFF;
            int yymm           = words[base + 2] & 0xFFFF;
            int ddhh           = words[base + 3] & 0xFFFF;
            int mmss           = words[base + 4] & 0xFFFF;

            // 4-4) 解碼成 LocalDateTime
            LocalDateTime eventTime = parsePlcTime(yymm, ddhh, mmss);

            // 4-5) 防止重複寫入（重啟或 index 倒退）
            if (repo.existsByAreaAndSeqIndex(AREA, idx)) {
                //log.debug("[BTN-{}] seq_index={} already exists, skip", AREA, idx);
                continue;
            }

            // 4-6) 寫入 DB
            ButtonLog row = new ButtonLog();
            row.setArea(AREA);
            row.setSeqIndex(idx);
            row.setButtonId((byte) buttonIdWord);
            row.setReturnCode((byte) returnCodeWord);
            row.setEventTime(eventTime);
            repo.save(row);

            // 4-7) 同時寫一筆 log，方便 trace
            String btnDesc = mapButtonId(buttonIdWord);
            String resDesc = mapResult(returnCodeWord);
            log.info("[BTN-{}] 新增紀錄 idx={} time={} buttonId={} ({}) result={} (rawReturn={})",
                    AREA, idx, eventTime, buttonIdWord, btnDesc, resDesc, returnCodeWord);
        }

        // 5) 更新 lastIndex，下一輪就從這裡往後接
        lastIndex = currIndex;
    }

    // -------------------------------------------------------------------------
    // 小工具：Button ID / Result 對應文字、BCD 時間轉換
    // -------------------------------------------------------------------------

    /**
     * 按鈕 ID 對應說明文字，方便 log 看得懂
     */
    private static String mapButtonId(int id) {
        return switch (id) {
            case 1 -> "啟動";
            case 2 -> "停止";
            case 3 -> "異常復歸";
            case 4 -> "手自動切換";
            case 5 -> "ZIP側維修門";
            case 6 -> "AMR側維修門";
            default -> "未知按鈕(" + id + ")";
        };
    }

    /**
     * ReturnCode 對應說明文字
     */
    private static String mapResult(int code) {
        return switch (code) {
            case 1 -> "OK";
            case 2 -> "NG";
            default -> "UNKNOWN(" + code + ")";
        };
    }

    /**
     * PLC BCD 時間格式 → LocalDateTime
     *
     * 參考：
     *   yymm: 高位 byte = YY (BCD)，低位 byte = MM (BCD)
     *   ddhh: 高位 byte = DD (BCD)，低位 byte = hh (BCD)
     *   mmss: 高位 byte = mm (BCD)，低位 byte = ss (BCD)
     *
     * 年份以 2000 為基準：2000 + YY
     */
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
            // 異常（例如日期不合法）時，避免整支掛掉，先回 now()
            return LocalDateTime.now();
        }
    }

    /**
     * BCD → int（例：0x24 → 24）
     */
    private static int bcdToInt(int bcd) {
        int hi = (bcd >> 4) & 0xF;
        int lo = bcd & 0xF;
        return hi * 10 + lo;
    }
}
