package com.czkuo.rdf88701.domain.plc.state.transfer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * TransferStateMachine
 * - 控制與追蹤單一 Transfer 的裝置主狀態
 * - 根據 PLC 傳回的 DeviceStatus 更新主狀態並偵測狀態變化
 * - 支援狀態比對、資料變更檢查與強制錯誤切換
 */
@Slf4j
@Getter
public class TransferStateMachine {

    /** 所屬 Transfer 編號 */
    private final int transferId;

    /** 當前主狀態 */
    private TransferState currentState;

    /** 最新一次收到的 DeviceStatus（供 UI 與外部使用） */
    private volatile TransferDeviceStatus latestDeviceStatus;

    /** 上一次成功推播或記錄的 DeviceStatus（供比對用） */
    private volatile TransferDeviceStatus previousDeviceStatus;

    /**
     * 建構新的狀態機
     */
    public TransferStateMachine(int transferId) {
        this.transferId = transferId;
        this.currentState = TransferState.UNKNOWN;
    }

    /**
     * 根據最新 DeviceStatus 推進狀態機
     *
     * @param status 最新 PLC 狀態
     * @return true = 有變化（流程或資料），false = 無變化
     */
    public boolean updateFromDeviceStatus(TransferDeviceStatus status) {
        if (status == null) {
            log.warn("[TransferSM] Transfer#{} status is null, skipping update.", transferId);
            return false;
        }

        this.latestDeviceStatus = status;

        TransferState newState = resolveStateFrom(status);
        TransferState oldState = this.currentState;

        boolean stateChanged = !newState.equals(this.currentState);
        boolean contentChanged = isContentChanged(this.previousDeviceStatus, status);

        if (stateChanged) {
            log.info("[TransferSM] Transfer#{} 狀態變更：{} → {}", transferId, oldState, newState);
            this.currentState = newState;
        } else if (contentChanged) {
            //log.debug("[TransferSM] Transfer#{} PLC資料變更（狀態未變）", transferId);
        }

        if (stateChanged || contentChanged) {
            this.previousDeviceStatus = TransferDeviceStatus.copyFrom(status);
        }

        return stateChanged || contentChanged;
    }

    /**
     * 根據設備回報的狀態碼決定主狀態（此邏輯依實際 Transfer 定義調整）
     */
    private TransferState resolveStateFrom(TransferDeviceStatus status) {
        if (status == null || !status.isAvailable()) {
            return TransferState.UNKNOWN;
        }

        int code = status.getTransferStatus().getRunningSubStatus() & 0xFF;
        return switch (code) {
            case 1 -> TransferState.IDLE;        // 閒置可用
            case 2 -> TransferState.PROCESSING;  // 執行中
            case 3 -> TransferState.COMPLETE;    // 完成
            default -> TransferState.UNKNOWN;
        };
    }

    /**
     * 比對兩筆資料內容是否有變化（僅比對關鍵欄位）
     */
    private boolean isContentChanged(TransferDeviceStatus prev, TransferDeviceStatus curr) {
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
