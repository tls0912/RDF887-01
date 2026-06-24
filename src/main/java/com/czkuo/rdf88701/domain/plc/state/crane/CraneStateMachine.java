package com.czkuo.rdf88701.domain.plc.state.crane;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * CraneStateMachine
 * - 控制與追蹤單一天車的裝置主狀態
 * - 根據 PLC 傳回的 DeviceStatus 更新主狀態並偵測狀態變化
 * - 支援狀態比對、資料變更檢查與強制錯誤切換
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Getter
public class CraneStateMachine {

    /** 所屬 Crane 編號 */
    private final int craneId;

    /** 當前主狀態 */
    private CraneState currentState;

    /** 最新一次收到的 DeviceStatus（供 UI 與外部使用） */
    private volatile CraneDeviceStatus latestDeviceStatus;

    /** 上一次成功推播或記錄的 DeviceStatus（供比對用） */
    private volatile CraneDeviceStatus previousDeviceStatus;

    /**
     * 建構新的狀態機
     */
    public CraneStateMachine(int craneId) {
        this.craneId = craneId;
        this.currentState = CraneState.UNKNOWN;
    }

    /**
     * 根據最新 DeviceStatus 推進狀態機
     *
     * @param status 最新 PLC 狀態
     * @return true = 有變化（流程或資料），false = 無變化
     */
    public boolean updateFromDeviceStatus(CraneDeviceStatus status) {
        if (status == null) {
            log.warn("[CraneStateMachine] Crane#{} status is null, skipping update.", craneId);
            return false;
        }

        this.latestDeviceStatus = status;

        CraneState newState = resolveStateFrom(status);
        CraneState oldState = this.currentState;

        boolean stateChanged = !newState.equals(this.currentState);
        boolean contentChanged = isContentChanged(this.previousDeviceStatus, status);

        if (stateChanged) {
            log.info("[CraneStateMachine] Crane#{} 狀態變更：{} → {}", craneId, oldState, newState);
            this.currentState = newState;
        } else if (contentChanged) {
            //log.debug("[CraneStateMachine] Crane#{} PLC資料變更（狀態未變）", craneId);
        }

        if (stateChanged || contentChanged) {
            this.previousDeviceStatus = CraneDeviceStatus.copyFrom(status);
        }

        return stateChanged || contentChanged;
    }

    /**
     * 根據設備回報的狀態碼決定主狀態（此邏輯可依實務調整）
     */
    private CraneState resolveStateFrom(CraneDeviceStatus status) {
        if (status == null || !status.isAvailable()) {
            return CraneState.UNKNOWN;
        }

        int code = status.getDeviceStatus() & 0xFF;
        return switch (code) {
            case 1 -> CraneState.HOME_WAITING;     // WAITING HOME ACTION
            case 2 -> CraneState.HOMING;           // Transfer Device HOME ACTION
            case 3 -> CraneState.IDLE;             // Transfer Device Idle
            case 4 -> CraneState.BUSY;             // Transfer Device Busy
            case 5 -> CraneState.ERROR;            // STOP (Y AXIS IS Not HOME POS.)
            case 6 -> CraneState.MAINTAIN;         // Transfer Device Maintain
            default -> CraneState.UNKNOWN;
        };
    }

    /**
     * 比對兩筆資料內容是否有變化（僅比對關鍵欄位）
     */
    private boolean isContentChanged(CraneDeviceStatus prev, CraneDeviceStatus curr) {
        if (prev == null || curr == null) return true;
        return prev.isContentDifferent(curr);
    }

    /**
     * 強制切換為錯誤狀態（用於 Alarm 應變）
     */
    public void forceError(String reason) {
        this.currentState = CraneState.ERROR;
        log.error("[CraneStateMachine] Crane#{} 強制切入 ERROR 狀態：{}", craneId, reason);
    }

    /**
     * 判斷 latestDeviceStatus 是否在指定秒數內更新
     */
    public boolean hasValidLatestStatus(long thresholdSeconds) {
        return latestDeviceStatus != null && latestDeviceStatus.isAvailable() && !latestDeviceStatus.isOverdue(thresholdSeconds);
    }
}
