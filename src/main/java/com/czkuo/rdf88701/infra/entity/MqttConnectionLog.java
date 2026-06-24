package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * <p>
 * MQTT 連線與斷線事件歷程表（可用於日誌、統計、通知）
 * </p>
 *
 * @author czkuo
 * @since 2025-08-05
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("mqtt_connection_log")
public class MqttConnectionLog {

    /**
     * 主鍵 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對方系統代碼（如 SEEC、ASE）
     */
    private String remoteSystem;

    /**
     * 連線狀態（CONNECTED=建立連線，DISCONNECTED=判斷斷線）
     */
    private String status;

    /**
     * 事件發生時間（如接收到 S001 / 判斷為中斷）
     */
    private LocalDateTime eventTime;

    /**
     * 原因或附註說明（可記錄 timeout、retry 次數等）
     */
    private String reason;

    /**
     * 建立時間
     */
    private LocalDateTime createdTime;
}
