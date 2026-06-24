package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttConnectionService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S001AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S001AckHandler
 * - 負責處理 CMD_ID=S001 的 ACK 訊息（即對方針對建立連線的回覆）。
 * - 一般在 SEEC、ASE 發出 S001 指令後，會收到對方的 ACK 作為回應。
 * - 此 Handler 處理邏輯包含：
 *   1. 記錄 ACK 訊息至 mqtt_message_log
 *   2. 更新對方連線狀態為 CONNECTED，並記錄 mqtt_connection_log 日誌
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S001AckHandler extends AbstractAckHandler<S001AckPayload> {

    private final MqttMessageLogService logService;
    private final MqttConnectionService connectionService;

    /**
     * 建構子
     *
     * @param objectMapper JSON 物件轉換器，用於反序列化與轉換 JsonNode
     * @param logService   封裝 MQTT 訊息記錄邏輯的服務（寫入 mqtt_message_log）
     * @param connectionService 管理連線狀態與記錄日誌
     */
    public S001AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService,
                          MqttConnectionService connectionService) {
        super(objectMapper);
        this.logService = logService;
        this.connectionService = connectionService;
    }

    /**
     * 實際執行 ACK 訊息的處理流程。
     * <p>
     * 1. 印出日誌顯示收到的版本資訊
     * 2. 將 payload 轉為 JsonNode
     * 3. 使用 logService 記錄 ACK 到資料庫
     * 4. 標記對方系統為 CONNECTED 並記錄連線日誌
     *
     * @param system 發送方系統代碼（如 seec / ase）
     * @param topic  MQTT 原始 topic（如 seec_to_saa）
     * @param ack    已反序列化的 ACK payload 資料
     */
    @Override
    protected void process(String system, String topic, S001AckPayload ack) throws Exception {
        // 1️⃣ 印出日誌：顯示回覆來源與版本資訊
        log.info("[S001] 收到 ACK：program={}, version={}, topic={}, system={}",
                ack.getMessage().getProgramName(),
                ack.getMessage().getVersion(),
                topic,
                system
        );

        // 2️⃣ 轉換 payload 為 JsonNode（後續記錄用）
        JsonNode jsonPayload = objectMapper.valueToTree(ack);

        // 3️⃣ 統一使用 MqttMessageLogService 記錄 ACK
        logService.record(
                topic,                          // MQTT topic（來源 topic）
                system,                         // sender：對方系統
                logService.getLocalSystem(),    // receiver：我方系統（從 yml 動態取得）
                jsonPayload,                    // payload：原始 JSON 結構
                MqttMessageType.ACK             // 訊息類型：ACK
        );

        // 4️⃣ 更新狀態為 connected 並寫入 mqtt_connection_log
        connectionService.markConnected(system, "收到 S001 ACK 成功");
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 快取註冊與支援判斷。
     *
     * @return "S001"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S001";
    }

    /**
     * 回傳 payload 對應的物件型別（提供給 Jackson 用於反序列化）。
     *
     * @return S001AckPayload.class
     */
    @Override
    protected Class<S001AckPayload> getAckType() {
        return S001AckPayload.class;
    }
}
