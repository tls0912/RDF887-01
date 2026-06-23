package com.czkuo.rdf88701.application.mqtt;

import com.czkuo.rdf88701.common.enums.MqttMessageType;

/**
 * 所有 MQTT 訊息處理器需實作此介面
 * - 根據 system / topic / payload / type 判斷是否支援
 * - 根據類型執行處理邏輯（COMMAND / ACK）
 */
public interface MqttMessageHandler {

    /**
     * 判斷是否支援處理該筆 MQTT 訊息
     *
     * @param system  來源系統（如 ase / seec）
     * @param topic   MQTT topic
     * @param payload MQTT 訊息內容（通常為 JSON 字串）
     * @param type    訊息類型（COMMAND / ACK）
     * @return 是否支援此訊息
     */
    boolean supports(String system, String topic, String payload, MqttMessageType type);

    /**
     * 處理該筆 MQTT 訊息
     *
     * @param system  來源系統
     * @param topic   MQTT topic
     * @param payload MQTT JSON 字串
     * @param type    訊息類型（COMMAND / ACK）
     */
    void handle(String system, String topic, String payload, MqttMessageType type);
}
