package com.czkuo.rdf88701.domain.plc.state.safety;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * SafetyDeviceStatus
 * - 封裝單一「安全裝置群組（Bank）」的 PLC 回應資料快照
 * - 用 addr -> state 的方式承載各點位（例：W1042.A -> true/false）
 * - 提供可用性/完整性/過期判斷、差異比對、連線資訊等
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class SafetyDeviceStatus {

    // === Meta 資訊 ===
    /** 對應 plc-safety.yml 的 device.id */
    private int deviceId;

    /** 對應 plc-safety.yml 的 device.name（例如 "Safety-Sensor-Bank"） */
    private String deviceName;

    /** 本次快照時間 */
    private Instant snapshotTime = Instant.now();

    // === 點位狀態 ===
    /** 位址 -> 狀態（true=觸發/導通，false=未觸發） */
    private final Map<String, Boolean> states = new LinkedHashMap<>();

    // === 狀態補充欄位（由系統補充）===
    private boolean available = true;  // 本次 decode 是否成功
    private boolean complete  = true;  // 是否為完整快照（若拆包可由外部標記）
    private boolean stale     = false; // 是否已過期（由監控器標記）

    // === 通訊狀態（可由 PLC 連線監控器注入） ===
    private boolean connected;
    private Instant lastConnectedTime;
    private Instant lastDisconnectedTime;

    /* ================= 便捷 API ================= */

    /** 設定/覆寫某個點位狀態（自動將 addr 正規化成大寫） */
    public void putState(String addr, boolean value) {
        if (addr != null) {
            states.put(addr.toUpperCase(), value);
        }
    }

    /** 取得某個點位狀態（大小寫不敏感） */
    public Boolean getState(String addr) {
        if (addr == null) return null;
        return states.get(addr.toUpperCase());
    }

    /** 只讀視圖（避免外部誤改） */
    public Map<String, Boolean> getStatesView() {
        return Collections.unmodifiableMap(states);
    }

    /* ================= 判斷邏輯 ================= */

    /** 是否超過門檻秒數未更新 */
    public boolean isOverdue(long thresholdSeconds) {
        return snapshotTime == null ||
                Duration.between(snapshotTime, Instant.now()).getSeconds() > thresholdSeconds;
    }

    /** 是否同時「可用 + 未過期 + 完整」 */
    public boolean isValidAndComplete(long thresholdSeconds) {
        return available && !isOverdue(thresholdSeconds) && complete;
    }

    /** 對 UI 呈現的簡易字串（僅摘要） */
    public String toSimpleString() {
        return String.format(
                "SafetyDevice#%d('%s') - points=%d, available=%s, complete=%s, stale=%s, connected=%s, ts=%s",
                deviceId, deviceName, states.size(), available, complete, stale, connected,
                snapshotTime != null ? snapshotTime.toString() : "null"
        );
    }

    /** 複製（淺拷貝 + 狀態 Map 深拷貝） */
    public static SafetyDeviceStatus copyFrom(SafetyDeviceStatus src) {
        if (src == null) return null;
        SafetyDeviceStatus copy = new SafetyDeviceStatus();
        copy.deviceId = src.deviceId;
        copy.deviceName = src.deviceName;
        copy.snapshotTime = src.snapshotTime;
        copy.available = src.available;
        copy.complete = src.complete;
        copy.stale = src.stale;
        copy.connected = src.connected;
        copy.lastConnectedTime = src.lastConnectedTime;
        copy.lastDisconnectedTime = src.lastDisconnectedTime;

        src.states.forEach((k, v) -> copy.states.put(k, v));
        return copy;
    }

    /** 與另一個快照是否內容不同（用於觸發事件判斷） */
    public boolean isContentDifferent(SafetyDeviceStatus other) {
        if (other == null) return true;
        if (this.deviceId != other.deviceId) return true;
        if (!safeEquals(this.deviceName, other.deviceName)) return true;
        if (this.available != other.available) return true;
        if (this.complete  != other.complete)  return true;

        // 點位數量不同或任一位址值不同
        if (this.states.size() != other.states.size()) return true;
        for (Map.Entry<String, Boolean> e : this.states.entrySet()) {
            Boolean ov = other.states.get(e.getKey());
            if (!Objects.equals(ov, e.getValue())) return true;
        }
        return false;
    }

    /** 設定連線狀態（比照 Gripper 的 withConnectionInfo） */
    public void setConnectedFromPlcStatus(boolean plcConnected, Instant lastConnected, Instant lastDisconnected) {
        this.connected = plcConnected;
        this.lastConnectedTime = lastConnected;
        this.lastDisconnectedTime = lastDisconnected;
    }

    public static SafetyDeviceStatus withConnectionInfo(SafetyDeviceStatus status, boolean plcConnected,
                                                        Instant lastConnected, Instant lastDisconnected) {
        status.setConnectedFromPlcStatus(plcConnected, lastConnected, lastDisconnected);
        return status;
    }

    /* ================= 小工具 ================= */

    private boolean safeEquals(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}
