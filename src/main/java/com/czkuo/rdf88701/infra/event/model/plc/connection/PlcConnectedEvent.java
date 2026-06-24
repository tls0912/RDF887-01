package com.czkuo.rdf88701.infra.event.model.plc.connection;

import com.czkuo.rdf88701.common.enums.ConnectionMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * 表示 PLC 裝置成功建立連線的事件
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlcConnectedEvent implements Serializable {

    private String deviceName;            // 裝置名稱（唯一識別）
    private Instant timestamp;            // 成功連線的時間
    private String ip;                    // 裝置 IP
    private String protocol;              // 通訊協議（如 mc、modbus）
    private String message;               // 補充說明文字（可空）
    private ConnectionMode mode;          // 建立當下的連線模式（AUTO / MANUAL / OFF）
    private Long latencyMs;               // 建立連線耗費的時間（毫秒）
    private Integer retryCount;           // 本次成功前累積的重試次數（如 0 表示首次）

    /**
     * 範例說明：
     * - 初始化時建立連線成功：mode=AUTO, retryCount=0
     * - 自動重連第 3 次成功：mode=AUTO, retryCount=3
     * - 外部操作觸發手動建立連線：mode=MANUAL, retryCount=0
     */
}
