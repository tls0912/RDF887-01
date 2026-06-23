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
 * MQTT 對外系統連線狀態快照表（每個對方系統僅一筆）
 * </p>
 *
 * @author czkuo
 * @since 2025-07-26
 */
@Getter
@Setter
@ToString
@TableName("mqtt_connection_state")
public class MqttConnectionState {

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
     * 目前是否連線中（true=連線，false=斷線）
     */
    private Boolean connected;

    /**
     * 最後一次成功建立連線時間（對方主動發出 S001，或我方發出 S001 並收到 ACK 時更新）
     */
    private LocalDateTime lastConnectedTime;

    /**
     * 最後一次收到 S002 ACK 的時間（用於監測心跳是否中斷）
     */
    private LocalDateTime lastHeartbeatTime;

    /**
     * 建立時間
     */
    private LocalDateTime createdTime;

    /**
     * 最後更新時間
     */
    private LocalDateTime updatedTime;
}
