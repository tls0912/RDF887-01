package com.czkuo.rdf88701.application.mqtt.handler;

import com.czkuo.rdf88701.application.mqtt.MqttMessageHandler;
import com.czkuo.rdf88701.application.mqtt.SupportsCommandId;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.mqtt.util.BaseMqttHandlerUtils;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * AbstractCommandHandler<T>
 * - 支援特定 CMD_ID 的 COMMAND / ACK 處理器抽象基底類別
 * - 自動解析 payload 與 dispatch 處理邏輯
 * - 使用 BaseMqttHandlerUtils 共用欄位抽取與驗證
 * - 預設檢查 TID 格式是否合法（yyyyMMddHHmmssSSS）
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractCommandHandler<T> implements MqttMessageHandler, SupportsCommandId {

    protected final ObjectMapper objectMapper;
    protected final MqttMessageEventPublisher responseEventPublisher;

    /**
     * 判斷是否支援此筆訊息（透過 CMD_ID 與 MessageType）
     */
    @Override
    public boolean supports(String system, String topic, String payload, MqttMessageType type) {
        if (!getSupportedTypes().contains(type)) return false;

        String cmdId = BaseMqttHandlerUtils.extractCmdId(objectMapper, payload);
        return cmdId != null && getCmdIdInternal().equalsIgnoreCase(cmdId);
    }

    /**
     * 處理收到的訊息（COMMAND 或 ACK）
     * - 包含 TID 欄位格式驗證（若非法則略過處理）
     */
    @Override
    public void handle(String system, String topic, String payload, MqttMessageType type) {
        try {
            // 驗證 TID 格式是否合法
            String tid = BaseMqttHandlerUtils.extractAndValidateTid(objectMapper, payload);
            if (tid == null) {
                log.warn("⚠️ TID 格式不正確，跳過處理。CMD_ID={} topic={}", getCmdIdInternal(), topic);
                return;
            }

            // 嚴格模式：多餘欄位直接報錯
            // ObjectMapper strictMapper = objectMapper.copy();
            // strictMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

            // T command = strictMapper.readValue(payload, getCommandType());

            T command = objectMapper.readValue(payload, getCommandType());
            process(system, topic, command, type);
        } catch (com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException e) {
            log.warn("❌ Payload 多了未定義欄位，CMD_ID={} topic={}, 欄位: {}, payload={}",
                    getCmdIdInternal(), topic, e.getPropertyName(), payload);
        } catch (Exception e) {
            log.error("❌ handle() 處理失敗，CMD_ID={}，error={}", getCmdIdInternal(), e.getMessage(), e);
        }
    }

    /**
     * 回傳此 Handler 支援的 CMD_ID（供快取註冊使用）
     */
    @Override
    public final String getCmdId() {
        return getCmdIdInternal();
    }

    /**
     * 預設支援的 MessageType（COMMAND）
     * - 子類別可覆寫支援多類型
     */
    @Override
    public Set<MqttMessageType> getSupportedTypes() {
        return Collections.unmodifiableSet(EnumSet.of(MqttMessageType.COMMAND));
    }

    // ======= 子類別需實作區 =======

    /**
     * 處理邏輯（COMMAND / ACK 均可）
     */
    protected abstract void process(String system, String topic, T command, MqttMessageType type) throws Exception;

    /**
     * 該 Handler 對應的 CMD_ID
     */
    protected abstract String getCmdIdInternal();

    /**
     * 對應的 payload 資料型別（for Jackson）
     */
    protected abstract Class<T> getCommandType();
}
