package com.czkuo.rdf88701.domain.plc.state.Strapping;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * StrappingStateMachine
 * - 控制與追蹤單一 Strapping 裝置的主狀態
 * - 根據 PLC 傳回的 DeviceStatus 更新主狀態並偵測狀態變化
 * - 支援狀態比對、資料變更檢查與強制錯誤切換
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Getter
public class StrappingStateMachine {

    /** 所屬 Strapping 編號 */
    private final int strappingId;

    /** 當前主狀態 */
    private StrappingState currentState;

    /** 最新一次收到的 DeviceStatus（供 UI 與外部使用） */
    private volatile StrappingDeviceStatus latestDeviceStatus;

    /** 上一次成功推播或記錄的 DeviceStatus（供比對用） */
    private volatile StrappingDeviceStatus previousDeviceStatus;

    /**
     * 建構新的狀態機
     */
    public StrappingStateMachine(int strappingId) {
        this.strappingId = strappingId;
        this.currentState = StrappingState.UNKNOWN;
    }

    /**
     * 根據最新 DeviceStatus 推進狀態機
     *
     * @param status 最新 PLC 狀態
     * @return true = 有變化（流程或資料），false = 無變化
     */
    public boolean updateFromDeviceStatus(StrappingDeviceStatus status) {
        if (status == null) {
            log.warn("[StrappingSM] Strapping#{} status is null, skipping update.", strappingId);
            return false;
        }

        this.latestDeviceStatus = status;

        StrappingState newState = resolveStateFrom(status);
        StrappingState oldState = this.currentState;

        boolean stateChanged = !newState.equals(this.currentState);
        boolean contentChanged = isContentChanged(this.previousDeviceStatus, status);

        if (stateChanged) {
            log.info("[StrappingSM] Strapping#{} 狀態變更：{} → {}", strappingId, oldState, newState);
            this.currentState = newState;
        } else if (contentChanged) {
            //log.debug("[StrappingSM] Strapping#{} PLC資料變更（狀態未變）", strappingId);
        }

        if (stateChanged || contentChanged) {
            this.previousDeviceStatus = StrappingDeviceStatus.copyFrom(status);
        }

        return stateChanged || contentChanged;
    }

    /**
     * 根據設備回報的狀態碼決定主狀態（此邏輯依實際 Strapping 定義調整）
     */
    private StrappingState resolveStateFrom(StrappingDeviceStatus status) {
        if (status == null || !status.isAvailable()) {
            return StrappingState.UNKNOWN;
        }

        int code = status.getStrappingStatus().getRunningSubStatus() & 0xFF;
        return switch (code) {
            case 1 -> StrappingState.IDLE;        // 閒置可用
            case 2 -> StrappingState.PROCESSING;  // 執行中
            case 3 -> StrappingState.COMPLETE;    // 完成
            default -> StrappingState.UNKNOWN;
        };
    }

    /**
     * 比對兩筆資料內容是否有變化（僅比對關鍵欄位）
     */
    private boolean isContentChanged(StrappingDeviceStatus prev, StrappingDeviceStatus curr) {
        if (prev == null || curr == null) return true;
        return prev.isContentDifferent(curr);
    }

    /**
     * 判斷 latestDeviceStatus 是否在指定秒數內更新
     */
    public boolean hasValidLatestStatus(long thresholdSeconds) {
        return latestDeviceStatus != null &&
                latestDeviceStatus.isAvailable() &&
                !latestDeviceStatus.isOverdue(thresholdSeconds);
    }
}
