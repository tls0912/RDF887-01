package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R029AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * R029AckHandler
 * - 負責處理 CMD_ID=R029 的 ACK 訊息（通知將貨搬去拆併打帶回覆）
 * - 廠商回覆 ASE 拆併打帶任務的接收、進度、錯誤狀態
 * - 處理流程包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. [可擴充] 根據回覆狀態處理 UI 顯示、異常提示、進度追蹤等
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
public class R029AckHandler extends AbstractAckHandler<R029AckPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /**
     * 建構子
     */
    public R029AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService) {
        super(objectMapper);
        this.logService = logService;
    }

    /**
     * 處理收到的 R029 ACK 訊息
     * <p>
     * 1. 記錄 ACK 至 mqtt_message_log
     * 2. [可擴充] 根據 result 狀態 UI 顯示、進度管理、異常提示
     */
    @Override
    protected void process(String system, String topic, R029AckPayload ack) throws Exception {
        R029AckPayload.Message msg = ack.getMessage();
        int count = (msg != null && msg.getCarrierList() != null) ? msg.getCarrierList().size() : 0;
        log.info("[R029] 收到拆併打帶任務 ACK：result={}, 批數={}, topic={}, system={}",
                ack.getResult(), count, topic, system);

        // 1️⃣ 記錄 ACK 至 mqtt_message_log
        JsonNode jsonPayload = objectMapper.valueToTree(ack);
        logService.record(
                topic,                        // MQTT topic
                system,                       // sender（對方系統）
                logService.getLocalSystem(),  // receiver（我方系統）
                jsonPayload,
                MqttMessageType.ACK
        );

        // 2️⃣ [可擴充] 根據 result = OK/NG/START/END 做 UI 顯示、進度追蹤、異常提示等
        // 目前僅保留 ACK 後續處理入口，尚未實作任務進度或異常顯示。
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     */
    @Override
    protected String getCmdIdInternal() {
        return "R029";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     */
    @Override
    protected Class<R029AckPayload> getAckType() {
        return R029AckPayload.class;
    }
}
