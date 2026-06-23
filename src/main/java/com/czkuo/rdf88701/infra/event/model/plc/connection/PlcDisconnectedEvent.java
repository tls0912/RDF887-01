package com.czkuo.rdf88701.infra.event.model.plc.connection;

import com.czkuo.rdf88701.common.enums.ConnectionMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * 表示 PLC 裝置發生斷線時的事件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlcDisconnectedEvent implements Serializable {

    private String deviceName;            // 裝置名稱
    private Instant timestamp;            // 發生時間
    private String ip;                    // 裝置 IP
    private String protocol;              // 通訊協定（如 mc、modbus）
    private String message;               // 補充錯誤訊息（可選）
    private ConnectionMode mode;          // 當下的連線模式（AUTO / MANUAL / OFF）
    private Reason reason;                // 詳細斷線原因分類

    /**
     * 表示斷線事件的發生原因，用於後續日誌記錄、統計分析與錯誤追蹤。
     */
    public enum Reason {

        /** 系統啟動時建立初始連線但失敗。 */
        INITIAL_CONNECT_FAILED,

        /** 輪詢（polling）過程中通訊異常。 */
        POLLING_FAILED,

        /** 裝置定期健康檢查時發現實際已斷線。 */
        HEALTH_CHECK_FAILED,

        /** 自動重連失敗。 */
        RECONNECT_FAILED,

        /** 執行通訊命令（如 read/write）時發生錯誤。 */
        COMMAND_FAILED,

        /** 外部操作導致中斷（例如使用者透過 UI/API 要求斷線）。 */
        EXTERNAL_DISCONNECT,

        /** 系統內部邏輯要求中斷（如模式切換為 OFF 或 MANUAL）。 */
        INTERNAL_DISCONNECT,

        /** 無法歸類的其他錯誤。 */
        UNKNOWN
    }
}
