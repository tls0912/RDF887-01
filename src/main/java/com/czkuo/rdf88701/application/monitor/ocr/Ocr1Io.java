package com.czkuo.rdf88701.application.monitor.ocr;

/**
 * Ocr1Io
 * -----------------------------------------------------------------------------
 * 將 PLC I/O 位址與固定常數抽出，供 Motion/Result 兩個 Monitor 共用。
 * 這裡僅是常數容器，無任何商業邏輯。
 */
public final class Ocr1Io {

    private Ocr1Io() {}

    // ===== 固定裝置名稱（點位一律用這個） =====
    public static final String DEVICE = "PLC-Packer";

    // ===== 條件：Transfer#3 必須在 VIRTUAL#5（Level 自行對應） =====
    public static final long   TRANSFER_ID   = 3L;
    public static final String TRANSFER_NAME = "Transfer#3";
    public static final int    TARGET_LEVEL  = 205;   // ← 依你的 PLC 映射調整

    // ===== OCR#1：Write Bits =====
    public static final String B_READY       = "B0220";
    public static final String B_COLLECT_REQ = "B0222";
    public static final String B_CMD_REQ     = "B0225";
    public static final String B_COMP_ACK    = "B0226";

    // ===== OCR#1：Read Bits =====
    public static final String B_STANDBY     = "B0820";
    public static final String B_COLLECT_ACK = "B0822";
    public static final String B_CMD_ACK     = "B0825";
    public static final String B_COMP_REQ    = "B0826";

    // ===== OCR#1：Write Words =====
    public static final String W_NO          = "W03B8";
    public static final String W_TYPE        = "W03B9"; // 低16: T；高16: q（沿用 packTypeAndQty）
    public static final String W_LOC1_H      = "W03BA"; // 厚度 mm×100（DEC）
    public static final String W_LOC2_BANK   = "W03BB"; // Bank（維持現況）
    public static final String W_LOC3_BAY    = "W03BC"; // Bay：1上 / 2下
    public static final String W_LOC4_LEVEL  = "W03BD"; // Level（維持現況）
    public static final String W_OCR_RETCODE = "W03BE"; // 0x0100 成功 / 0x0F00 失敗（回報 PLC）

    // ===== OCR#1：Read Words =====
    public static final String W_POS_BAY     = "W13B8";
    public static final String W_POS_LEVEL   = "W13B9";
    public static final String W_POS_BANK    = "W13BA";
    public static final String W_STATUS      = "W13BB"; // s:1/2/3（IDLE/PROCESSING/COMPLETE）
    public static final String W_RETCODE     = "W13BE"; // 0100/0800/0F00（MOVE 回傳碼，用於 log）

    // ===== 常數 =====
    public static final int TYPE_MOVE = 1;              // T=0001
    public static final int BAY_UP    = 1;
    public static final int BAY_DOWN  = 2;
    public static final int DEFAULT_THICK_MMx100 = 0;
    public static final int NO_FOR_EMPTY = 0;
    public static final int DEFAULT_QTY_WHEN_EMPTY = 0;

    // ===== Watchdog/Timeout（可依現場調） =====
    public static final long CMD_ACK_TIMEOUT_MS     = 5_000;   // 下命令未獲 CMD_ACK → 回收 CMD_REQ
    public static final long COMP_CLOSE_TIMEOUT_MS  = 5_000;   // COMP_ACK 拉太久 → 放掉
    public static final long COLLECT_ACK_TIMEOUT_MS = 5_000;   // Collect ACK 超時 → 強制放掉

    // ===== OCR 任務行為（ResultMonitor 用） =====
    public static final int  MAX_OCR_RETRY         = 3;
    public static final long POLL_MIN_INTERVAL_MS  = 1000;
    public static final long OCR_RESULT_TTL_MS     = 180_000;  // 等待結果（DB 或 vendor）的上限
}
