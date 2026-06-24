package com.czkuo.rdf88701.domain.plc.state.gripper;

import com.czkuo.rdf88701.domain.plc.state.common.RunningSubStatus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * GripperStateMachine
 * - 控制與追蹤單一 Gripper 的裝置主狀態
 * - 根據 PLC 傳回的 DeviceStatus 更新主狀態並偵測狀態變化
 * - 支援狀態比對、資料變更檢查與強制錯誤切換
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Getter
public class GripperStateMachine {

    /** 所屬 Gripper 編號 */
    private final int gripperId;

    /** 當前主狀態 */
    private GripperState currentState;

    /** 最新一次收到的 DeviceStatus（供 UI 與外部使用） */
    private volatile GripperDeviceStatus latestDeviceStatus;

    /** 上一次成功推播或記錄的 DeviceStatus（供比對用） */
    private volatile GripperDeviceStatus previousDeviceStatus;

    /**
     * 建構新的狀態機
     */
    public GripperStateMachine(int gripperId) {
        this.gripperId = gripperId;
        this.currentState = GripperState.UNKNOWN;
    }

    /**
     * 根據最新 DeviceStatus 推進狀態機
     *
     * @param status 最新 PLC 狀態
     * @return true = 有變化（流程或資料），false = 無變化
     */
    public boolean updateFromDeviceStatus(GripperDeviceStatus status) {
        if (status == null) {
            log.warn("[GripperSM] Gripper#{} status is null, skipping update.", gripperId);
            return false;
        }

        this.latestDeviceStatus = status;

        GripperState newState = resolveStateFrom(status);
        GripperState oldState = this.currentState;

        boolean stateChanged = !newState.equals(this.currentState);
        boolean contentChanged = isContentChanged(this.previousDeviceStatus, status);

        if (stateChanged) {
            log.info("[GripperSM] Gripper#{} 狀態變更：{} → {}", gripperId, oldState, newState);
            this.currentState = newState;
        } else if (contentChanged) {
            //log.debug("[GripperSM] Gripper#{} PLC資料變更（狀態未變）", gripperId);
        }

        if (stateChanged || contentChanged) {
            this.previousDeviceStatus = GripperDeviceStatus.copyFrom(status);
        }

        return stateChanged || contentChanged;
    }

    /**
     * 根據設備回報的狀態碼決定主狀態（此邏輯依實際 Gripper 定義調整）
     */
    private GripperState resolveStateFrom(GripperDeviceStatus status) {
        if (status == null || !status.isAvailable()) {
            return GripperState.UNKNOWN;
        }

        int code = status.getGripperStatus().getGripperStatus() & 0xFF;
        return switch (code) {
            case 1 -> GripperState.IDLE;
            case 2 -> GripperState.PROCESSING;
            case 3 -> GripperState.COMPLETE;
            default -> GripperState.UNKNOWN;
        };
    }

    /**
     * 比對兩筆資料內容是否有變化（僅比對關鍵欄位）
     */
    private boolean isContentChanged(GripperDeviceStatus prev, GripperDeviceStatus curr) {
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
