package com.czkuo.rdf88701.application.mqtt.handler;

import com.czkuo.rdf88701.application.mqtt.MqttMessageHandler;
import com.czkuo.rdf88701.application.mqtt.SupportsCommandId;
import com.czkuo.rdf88701.application.mqtt.util.BaseMqttHandlerUtils;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;


/**
 * MQTT ACK 處理器抽象基底。
 *
 * <p>負責 ACK 類訊息共用的型別檢查、CMD_ID 比對、TID 驗證與反序列化。
 * 子類別只需宣告 ACK payload 型別，並實作收到 ACK 後的業務處理。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractAckHandler<T> implements MqttMessageHandler, SupportsCommandId {

    protected final ObjectMapper objectMapper;

    /**
     * 判斷是否支援此 CMD_ID 的 ACK 訊息
     */
    @Override
    public boolean supports(String system, String topic, String payload, MqttMessageType type) {
        if (type != MqttMessageType.ACK) return false;

        String cmdId = BaseMqttHandlerUtils.extractCmdId(objectMapper, payload);
        return cmdId != null && getCmdIdInternal().equalsIgnoreCase(cmdId);
    }

    /**
     * 執行處理邏輯（ACK），包含 TID 驗證
     */
    @Override
    public void handle(String system, String topic, String payload, MqttMessageType type) {
        try {
            // 驗證 TID 格式是否合法
            String tid = BaseMqttHandlerUtils.extractAndValidateTid(objectMapper, payload);
            if (tid == null) {
                log.warn("⚠️ 忽略非法 TID 訊息，CMD_ID={}，payload={}", getCmdIdInternal(), payload);
                return;
            }

            // 反序列化並處理
            T ack = objectMapper.readValue(payload, getAckType());
            process(system, topic, ack);

        } catch (Exception e) {
            log.error("❌ handle() 處理 ACK 失敗：CMD_ID={}，payload={}，error={}", getCmdIdInternal(), payload, e.getMessage(), e);
        }
    }

    /**
     * 回傳此 Handler 支援的 CMD_ID（供 Router 註冊）
     */
    @Override
    public final String getCmdId() {
        return getCmdIdInternal();
    }

    /**
     * 回報支援類型：僅 ACK
     */
    @Override
    public Set<MqttMessageType> getSupportedTypes() {
        return Collections.unmodifiableSet(EnumSet.of(MqttMessageType.ACK));
    }

    // ========= 子類別需實作 =========

    /**
     * 執行實際的 ACK 處理邏輯
     */
    protected abstract void process(String system, String topic, T ack) throws Exception;

    /**
     * 對應 CMD_ID
     */
    protected abstract String getCmdIdInternal();

    /**
     * 反序列化所需的 ACK payload 類型
     */
    protected abstract Class<T> getAckType();
}
