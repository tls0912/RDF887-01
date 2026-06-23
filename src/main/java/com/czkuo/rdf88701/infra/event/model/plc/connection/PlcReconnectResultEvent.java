package com.czkuo.rdf88701.infra.event.model.plc.connection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * 裝置補連線結束時的事件（成功或失敗）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlcReconnectResultEvent implements Serializable {
    private String deviceName;
    private Instant timestamp;
    private boolean success;
    private int attemptNo;
    private String errorMessage;
}
