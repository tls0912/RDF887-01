package com.czkuo.rdf88701.common.dto;

import com.czkuo.rdf88701.common.enums.ProcessStatus;
import lombok.Value;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Value
public class DeviceProcessState {
    String deviceName;      // WIP / ZIPA / ZIPB
    ProcessStatus status;   // RUN/IDLE/STOP/WARNING/ERROR
    String message;         // 補充訊息
}
