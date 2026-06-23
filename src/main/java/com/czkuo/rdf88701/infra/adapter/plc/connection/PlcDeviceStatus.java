package com.czkuo.rdf88701.infra.adapter.plc.connection;

import com.czkuo.rdf88701.common.enums.ConnectionMode;
import com.czkuo.rdf88701.config.plc.PlcProperties;
import lombok.Data;

import java.time.Instant;

/**
 * 表示單一 PLC 裝置的即時狀態與控制意圖（非設定檔）
 */
@Data
public class PlcDeviceStatus {

    /** 是否目前已建立實體連線 */
    private boolean connected;

    /** 當前的連線模式（來自設定，可由外部控制變更） */
    private ConnectionMode connectionMode;

    /** 是否希望參與輪詢（可由外部修改） */
    private boolean shouldBePolled;

    /** 最後異常訊息（由連線或讀寫失敗產生） */
    private String lastError;

    /** 最後成功連線時間 */
    private Instant lastConnectedTime;

    /** 最後中斷連線時間 */
    private Instant lastDisconnectedTime;

    /** 當前累積的重連次數（每次失敗會遞增） */
    private int reconnectAttempts;

    public PlcDeviceStatus() {
        this.connected = false;
        this.connectionMode = ConnectionMode.AUTO;
        this.shouldBePolled = true;
        this.reconnectAttempts = 0;
    }

    /**
     * 從裝置設定建立初始狀態
     */
    public static PlcDeviceStatus from(PlcProperties.Device device) {
        PlcDeviceStatus status = new PlcDeviceStatus();
        status.setConnectionMode(device.getConnectionMode());
        status.setShouldBePolled(device.isDefaultPollingEnabled());
        return status;
    }

    /**
     * 標記為已連線成功
     */
    public void markConnected() {
        this.connected = true;
        this.lastConnectedTime = Instant.now();
        this.lastError = null;
        this.reconnectAttempts = 0;
    }

    /**
     * 標記為已斷線並記錄錯誤
     */
    public void markDisconnected(String errorMessage) {
        this.connected = false;
        this.lastDisconnectedTime = Instant.now();
        this.lastError = errorMessage;
        this.reconnectAttempts++;
    }

    /**
     * 取得並遞增目前的重連次數（提供給 scheduler 取得當前次數用）
     */
    public int incrementReconnectAttempts() {
        return ++this.reconnectAttempts;
    }

    /**
     * 重設重連次數（可由外部調用）
     */
    public void resetReconnectAttempts() {
        this.reconnectAttempts = 0;
    }

    /**
     * 判斷目前是否允許系統主動建立或維持連線（對應 AUTO 模式）
     */
    public boolean isAutoConnectEnabled() {
        return this.connectionMode == ConnectionMode.AUTO;
    }
}
