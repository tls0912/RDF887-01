package com.czkuo.rdf88701.infra.event.model.plc.safety;

import com.czkuo.rdf88701.domain.plc.state.safety.SafetyDeviceStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * 單一安全點位變更事件
 * - 與 SafetyPollingHandler.diffToEvents(...) 的建構子一致
 * - 用於批次推播時傳遞一個個點位的變化
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@ToString
@AllArgsConstructor
public class SafetyStatusUpdatedEvent {

    /** 安全設備群組（Bank）ID */
    private final int deviceId;

    /** 安全設備群組（Bank）名稱（例：Safety-Sensor-Bank） */
    private final String deviceName;

    /** 變更的點位位址（例：W1042.A） */
    private final String addr;

    /** 當前狀態（true=觸發/ON，false=未觸發/OFF） */
    private final boolean on;

    /** 此次快照時間 */
    private final Instant snapshotTime;
}
