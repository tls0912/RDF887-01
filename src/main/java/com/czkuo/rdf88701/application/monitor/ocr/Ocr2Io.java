package com.czkuo.rdf88701.application.monitor.ocr;

/**
 * OCR#2 的 PLC I/O 常數、站名與 Level、裝置編號等共用定義。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public final class Ocr2Io {

    private Ocr2Io() {}

    // PLC 裝置
    public static final String DEVICE = "PLC-Packer";

    // 站名
    public static final String SITE11_NAME = "Site#11";
    public static final String SITE12_NAME = "Site#12";
    public static final String SITE13_NAME = "Site#13";
    public static final String SITE14_NAME = "Site#14";
    public static final String SITE26_NAME = "Site#26";
    public static final String SITE27_NAME = "Site#27";
    public static final String SITE37_NAME = "Site#37";

    // Level（依映射）
    public static final int SITE11_LEVEL = 11;
    public static final int SITE12_LEVEL = 12;
    public static final int SITE13_LEVEL = 13;
    public static final int SITE14_LEVEL = 14;

    // TR8 的虛擬站點
    public static final int LEVEL_VIRTUAL_12 = 212;

    // 關聯裝置 ID
    public static final long TRANSFER_4 = 4L; // 對應 Site#12
    public static final long TRANSFER_5 = 5L; // 對應 Site#14
    public static final long TRANSFER_8 = 8L; // 第二組額外條件
    public static final long GRIPPER_6  = 6L; // 對應 Site#12
    public static final long GRIPPER_7  = 7L; // 對應 Site#14

    // OCR2 裝置編號（供派單）
    public static final int OCR_DEVICE_ID = 2;

    // ===== OCR#2：Write Bits =====
    public static final String B_READY        = "B0228";
    public static final String B_COLLECT_REQ  = "B022A";
    public static final String B_CMD_REQ      = "B022D";
    public static final String B_COMP_ACK     = "B022E";

    // ===== OCR#2：Read Bits =====
    public static final String B_STANDBY      = "B0828";
    public static final String B_COLLECT_ACK  = "B082A";
    public static final String B_CMD_ACK      = "B082D";
    public static final String B_COMP_REQ     = "B082E";

    // ===== OCR#2：Write Words =====
    public static final String W_NO           = "W03C0";
    public static final String W_TYPE         = "W03C1"; // 低16: T；高16: q（沿用 packTypeAndQty）
    public static final String W_LOC1_H       = "W03C2"; // 厚度 mm×100（DEC）
    public static final String W_LOC2_BANK    = "W03C3";
    public static final String W_LOC3_BAY     = "W03C4"; // 1:上 / 2:下
    public static final String W_LOC4_LEVEL   = "W03C5"; // 要到的站點 Level
    public static final String W_OCR_RETCODE  = "W03C6"; // 0100/0F00（Result 端回報）

    // ===== OCR#2：Read Words =====
    public static final String W_POS_BAY      = "W13C0"; // 0移動中 / 1上 / 2下
    public static final String W_POS_LEVEL    = "W13C1"; // 站點
    public static final String W_POS_BANK     = "W13C2";
    public static final String W_STATUS       = "W13C3"; // s:1/2/3; r:1/2
    public static final String W_MOVE_RETCODE = "W13C6"; // 0100/0800/0F00

    // 常數
    public static final int TYPE_MOVE = 1; // T=0001
    public static final int BAY_UP    = 1;
    public static final int BAY_DOWN  = 2;

    public static final int DEFAULT_THICK_MMx100     = 0;
    public static final int NO_FOR_EMPTY             = 0;
    public static final int DEFAULT_QTY_WHEN_EMPTY   = 0;

    // Result 端用的超時
    public static final long COLLECT_ACK_TIMEOUT_MS = 5_000;
    public static final long OCR_RESULT_TTL_MS      = 180_000;
    public static final int  MAX_OCR_RETRY          = 3;
    public static final long POLL_MIN_INTERVAL_MS   = 1000;
    public static final long DEVICE_STATUS_POLL_MIN_INTERVAL_MS = 3_000;
}
