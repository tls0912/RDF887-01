package com.czkuo.rdf88701.application.mqtt;

import com.czkuo.rdf88701.common.enums.MqttMessageType;

import java.util.Set;

/**
 * 提供 CMD_ID 供快取註冊
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface SupportsCommandId {
    String getCmdId();

    /**
     * 回傳該 Handler 支援的訊息類型（預設為 COMMAND）
     */
    default Set<MqttMessageType> getSupportedTypes() {
        return Set.of(MqttMessageType.COMMAND);
    }
}
