package com.czkuo.rdf88701.domain.plc.state.workingbeam;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * WorkingBeamStateMachine
 * - 控制與追蹤單一 WorkingBeam 的裝置主狀態
 * - 根據 PLC 傳回的 DeviceStatus 更新主狀態並偵測狀態變化
 * - 支援狀態比對、資料變更檢查與強制錯誤切換
 */
@Slf4j
@Getter
public class WorkingBeamStateMachine {

    /** 所屬 WorkingBeam 編號 */
    private final int workingBeamId;

    /** 當前主狀態 */
    private WorkingBeamState currentState;

    /** 最新一次收到的 DeviceStatus（供 UI 與外部使用） */
    private volatile WorkingBeamDeviceStatus latestDeviceStatus;

    /** 上一次成功推播或記錄的 DeviceStatus（供比對用） */
    private volatile WorkingBeamDeviceStatus previousDeviceStatus;

    /**
     * 建構新的狀態機
     */
    public WorkingBeamStateMachine(int workingBeamId) {
        this.workingBeamId = workingBeamId;
        this.currentState = WorkingBeamState.UNKNOWN;
    }

    /**
     * 根據最新 DeviceStatus 推進狀態機
     *
     * @param status 最新 PLC 狀態
     * @return true = 有變化（流程或資料），false = 無變化
     */
    public boolean updateFromDeviceStatus(WorkingBeamDeviceStatus status) {
        if (status == null) {
            log.warn("[WorkingBeamSM] Beam#{} status is null, skipping update.", workingBeamId);
            return false;
        }

        this.latestDeviceStatus = status;

        WorkingBeamState newState = resolveStateFrom(status);
        WorkingBeamState oldState = this.currentState;

        boolean stateChanged = !newState.equals(this.currentState);
        boolean contentChanged = isContentChanged(this.previousDeviceStatus, status);

        if (stateChanged) {
            log.info("[WorkingBeamSM] Beam#{} 狀態變更：{} → {}", workingBeamId, oldState, newState);
            this.currentState = newState;
        } else if (contentChanged) {
            //log.debug("[WorkingBeamSM] Beam#{} PLC資料變更（狀態未變）", workingBeamId);
        }

        if (stateChanged || contentChanged) {
            this.previousDeviceStatus = WorkingBeamDeviceStatus.copyFrom(status);
        }

        return stateChanged || contentChanged;
    }

    /**
     * 根據設備回報的狀態碼決定主狀態（此邏輯依實際 WorkingBeam 定義調整）
     */
    private WorkingBeamState resolveStateFrom(WorkingBeamDeviceStatus status) {
        if (status == null || !status.isAvailable()) {
            return WorkingBeamState.UNKNOWN;
        }

        int code = status.getWorkingBeamStatus().getRunningSubStatus() & 0xFF;
        return switch (code) {
            case 1 -> WorkingBeamState.IDLE;          // 閒置可用
            case 2 -> WorkingBeamState.PROCESSING;    // 執行中
            case 3 -> WorkingBeamState.COMPLETE;      // 完成
            default -> WorkingBeamState.UNKNOWN;
        };
    }

    /**
     * 比對兩筆資料內容是否有變化（僅比對關鍵欄位）
     */
    private boolean isContentChanged(WorkingBeamDeviceStatus prev, WorkingBeamDeviceStatus curr) {
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
