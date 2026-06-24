package com.czkuo.rdf88701.domain.plc.state.infrared;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * InfraredStateMachine
 * - 控制與追蹤單一紅外線設備的主狀態
 * - 根據 PLC 傳回的 DeviceStatus 更新狀態機
 * - 支援狀態比對、資料變更檢查與有效性判斷
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Getter
public class InfraredStateMachine {

    /** 紅外線設備 ID */
    private final int infraredId;

    /** 當前主狀態 */
    private InfraredState currentState;

    /** 最新設備狀態（Polling 回來） */
    private volatile InfraredDeviceStatus latestDeviceStatus;

    /** 上一次處理過的設備狀態（供比對） */
    private volatile InfraredDeviceStatus previousDeviceStatus;

    public InfraredStateMachine(int infraredId) {
        this.infraredId = infraredId;
        this.currentState = InfraredState.UNKNOWN;
    }

    /**
     * 使用最新裝置狀態推進狀態機流程
     *
     * @param status PLC 回傳的狀態封裝物件
     * @return true: 狀態或資料有變更，false: 無變化
     */
    public boolean updateFromDeviceStatus(InfraredDeviceStatus status) {
        if (status == null) {
            log.warn("[InfraredSM] Infrared#{} status is null, skipping update.", infraredId);
            return false;
        }

        this.latestDeviceStatus = status;

        InfraredState newState = resolveStateFrom(status);
        InfraredState oldState = this.currentState;

        boolean stateChanged = !newState.equals(this.currentState);
        boolean contentChanged = isContentChanged(this.previousDeviceStatus, status);

        if (stateChanged) {
            log.info("[InfraredSM] Infrared#{} 狀態變更：{} → {}", infraredId, oldState, newState);
            this.currentState = newState;
        } else if (contentChanged) {
            //log.debug("[InfraredSM] Infrared#{} 資料變更（狀態未變）", infraredId);
        }

        if (stateChanged || contentChanged) {
            this.previousDeviceStatus = InfraredDeviceStatus.copyFrom(status);
        }

        return stateChanged || contentChanged;
    }

    /**
     * 根據 PLC 回報的裝置狀態碼轉換為主狀態（主態邏輯）
     */
    private InfraredState resolveStateFrom(InfraredDeviceStatus status) {
        if (status == null || !status.isAvailable()) {
            return InfraredState.UNKNOWN;
        }

        int code = status.getDeviceStatusCode();  // 來源：W1363 - 最後 2 byte
        return switch (code) {
            case 1 -> InfraredState.IDLE;
            case 2 -> InfraredState.WAIT_CMD;
            case 3 -> InfraredState.PROCESSING;
            case 4 -> InfraredState.COMPLETE;
            default -> InfraredState.UNKNOWN;
        };
    }

    private boolean isContentChanged(InfraredDeviceStatus prev, InfraredDeviceStatus curr) {
        if (prev == null || curr == null) return true;
        return prev.isContentDifferent(curr);
    }

    public boolean hasValidLatestStatus(long thresholdSeconds) {
        return latestDeviceStatus != null &&
                latestDeviceStatus.isAvailable() &&
                !latestDeviceStatus.isOverdue(thresholdSeconds);
    }
}
