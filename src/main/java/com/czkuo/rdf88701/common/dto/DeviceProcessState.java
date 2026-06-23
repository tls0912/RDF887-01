package com.czkuo.rdf88701.common.dto;

import com.czkuo.rdf88701.common.enums.ProcessStatus;
import lombok.Value;

@Value
public class DeviceProcessState {
    String deviceName;      // WIP / ZIPA / ZIPB
    ProcessStatus status;   // RUN/IDLE/STOP/WARNING/ERROR
    String message;         // 補充訊息
}
