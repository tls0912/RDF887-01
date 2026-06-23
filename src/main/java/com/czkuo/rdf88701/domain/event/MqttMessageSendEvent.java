package com.czkuo.rdf88701.domain.event;

import com.czkuo.rdf88701.common.enums.MqttMessageType;

/**
 * MqttMessageSendEvent
 * - 表示一筆要送出的 MQTT 訊息
 * - 由應用層發出，基礎設施層接收並實際發送
 *
 * - system：目標系統（如 seec、ase）
 * - payload：完整 JSON 指令
 * - type：訊息類型（COMMAND 或 ACK）
 * - tid：指令識別碼（格式 yyyyMMddHHmmssSSS）
 * - cmdId：指令代碼（如 S001、R007）
 */
public record MqttMessageSendEvent(
        String system,
        String payload,
        MqttMessageType type,
        String tid,
        String cmdId
) {}
