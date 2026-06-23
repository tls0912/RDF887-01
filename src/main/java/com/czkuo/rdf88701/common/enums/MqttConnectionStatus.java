package com.czkuo.rdf88701.common.enums;

/**
 * MqttConnectionStatus
 * <p>
 * 表示與對方系統（如 ASE、SEEC）之間的邏輯連線狀態。
 * 此狀態為應用層自行判斷之結果，與 MQTT Broker 的 TCP 狀態不同。
 * </p>
 *
 * <p>
 * 使用場景：
 * <ul>
 *     <li>mqtt_connection_state 表：記錄目前每個對象的連線狀態</li>
 *     <li>mqtt_connection_log 表：記錄每次狀態變更事件（建立、斷線）</li>
 *     <li>應用邏輯中判斷是否可送指令、是否重發握手等</li>
 * </ul>
 * </p>
 *
 * <p>
 * 判斷邏輯：
 * <ul>
 *     <li>CONNECTED：成功建立邏輯握手（例如接收到對方 S001，或我方 S001 有 ACK）</li>
 *     <li>DISCONNECTED：超過心跳時間（未收到 S002 ACK）或連線異常</li>
 * </ul>
 * </p>
 *
 * ⚠ 注意：此狀態不可直接等同於 MQTT 連線層的 keep-alive，需透過應用層判斷維護。
 */
public enum MqttConnectionStatus {

    /**
     * 已建立邏輯連線（有收到 S001 並 ACK，或雙方握手成功）
     */
    CONNECTED,

    /**
     * 邏輯判定為已中斷（如心跳逾時、長時間無回應）
     */
    DISCONNECTED
}
