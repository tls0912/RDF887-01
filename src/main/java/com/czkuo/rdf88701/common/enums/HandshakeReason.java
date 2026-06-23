package com.czkuo.rdf88701.common.enums;

public enum HandshakeReason {
    STARTUP,        // 開機
    HB_TIMEOUT,     // 心跳逾時後重連
    MANUAL,         // 手動觸發
    BROKER_RECOVER, // Broker復原
    OTHER
}
