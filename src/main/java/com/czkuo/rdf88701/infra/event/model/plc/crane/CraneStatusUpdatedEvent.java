package com.czkuo.rdf88701.infra.event.model.plc.crane;

import com.czkuo.rdf88701.domain.plc.state.crane.CraneDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneState;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * CraneStatusUpdatedEvent
 * - 表示單一 Crane 狀態變化事件
 * - 可推送給 Kafka/MQ/UI 或儲存日誌
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@ToString
@RequiredArgsConstructor
public class CraneStatusUpdatedEvent {

    /** Crane 編號 */
    private final int craneId;

    /** 最新狀態資料（完整快照） */
    private final CraneDeviceStatus deviceStatus;

    /** 當前主狀態（例如：IDLE / BUSY / ERROR） */
    private final CraneState currentState;

    /**
     * 取得產品 ID
     */
    public String getProductId() {
        return deviceStatus != null ? deviceStatus.getProductId() : null;
    }

    /**
     * 取得位置資訊（格式化字串）
     */
    public String getFormattedLocation() {
        if (deviceStatus == null) return "-";
        return String.format("Bank:%d, Bay:%d, Level:%d",
                deviceStatus.getBankPosition(),
                deviceStatus.getBayPosition(),
                deviceStatus.getLevelPosition());
    }

    /**
     * 取得主要狀態碼（HEX）
     */
    public String getStatusHex() {
        if (deviceStatus == null) return "-";
        return String.format("0x%04X", deviceStatus.getDeviceStatus());
    }
}