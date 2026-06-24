package com.czkuo.rdf88701.infra.lock;

import java.util.concurrent.atomic.AtomicBoolean;

/** 極簡 in-process 互斥：同 JVM 內有效；不跨程序、不重開機保存 */
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public final class InProcLocks {
    // GP4@25 / WB5 / WB8
    private static final AtomicBoolean GP4_25_ACTIVE = new AtomicBoolean(false);
    private static final AtomicBoolean WB5_ACTIVE    = new AtomicBoolean(false);
    private static final AtomicBoolean WB8_ACTIVE    = new AtomicBoolean(false);

    // GP5@36 / WB6 / TR8
    private static final AtomicBoolean GP5_36_ACTIVE = new AtomicBoolean(false);
    private static final AtomicBoolean WB6_ACTIVE    = new AtomicBoolean(false);
    private static final AtomicBoolean TR8_ACTIVE    = new AtomicBoolean(false);

    private InProcLocks() {}

    // ───────── GP4(Site#25) ⟂ WB5 ─────────
    public static boolean tryEnterGp4Site25() {
        // WB5 活動中 → 禁止 GP4@25
        if (WB5_ACTIVE.get()) return false;
        return GP4_25_ACTIVE.compareAndSet(false, true);
    }
    public static void exitGp4Site25() { GP4_25_ACTIVE.set(false); }

    // ───────── WB5 ⟂ (GP4@25, WB8) ─────────
    public static boolean tryEnterWb5() {
        if (GP4_25_ACTIVE.get() || WB8_ACTIVE.get()) return false;
        return WB5_ACTIVE.compareAndSet(false, true);
    }
    public static void exitWb5() { WB5_ACTIVE.set(false); }

    // ───────── WB8 ⟂ WB5 ─────────
    public static boolean tryEnterWb8() {
        if (WB5_ACTIVE.get()) return false;
        return WB8_ACTIVE.compareAndSet(false, true);
    }
    public static void exitWb8() { WB8_ACTIVE.set(false); }

    // ───────── GP5(Site#36) ⟂ WB6 ─────────
    public static boolean tryEnterGp5Site36() {
        if (WB6_ACTIVE.get()) return false;
        return GP5_36_ACTIVE.compareAndSet(false, true);
    }
    public static void exitGp5Site36() { GP5_36_ACTIVE.set(false); }

    // ───────── WB6 ⟂ (GP5@36, TR8) ─────────
    public static boolean tryEnterWb6() {
        if (GP5_36_ACTIVE.get() || TR8_ACTIVE.get()) return false;
        return WB6_ACTIVE.compareAndSet(false, true);
    }
    public static void exitWb6() { WB6_ACTIVE.set(false); }

    // ───────── TR8 ⟂ WB6 ─────────
    public static boolean tryEnterTr8() {
        if (WB6_ACTIVE.get()) return false;
        return TR8_ACTIVE.compareAndSet(false, true);
    }
    public static void exitTr8() { TR8_ACTIVE.set(false); }
}
