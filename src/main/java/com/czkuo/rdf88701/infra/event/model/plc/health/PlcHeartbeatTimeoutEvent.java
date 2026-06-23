package com.czkuo.rdf88701.infra.event.model.plc.health;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * 心跳逾時或未回應的錯誤事件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlcHeartbeatTimeoutEvent implements Serializable {
    private String deviceName;
    private Instant timestamp;
    private String error;
}
