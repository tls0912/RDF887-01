package com.czkuo.rdf88701.application.mqtt.handler.ack;

import com.czkuo.rdf88701.application.mqtt.handler.AbstractAckHandler;
import com.czkuo.rdf88701.application.service.mqtt.MqttConnectionService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S002AckPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S002AckHandler
 * - 負責處理 CMD_ID=S002 的 ACK 訊息（即對方針對心跳指令的回覆）。
 * - 通常在我方發出 S002（心跳）後，對方會回傳 ACK 表示連線仍正常。
 * - 此 Handler 處理邏輯包含：
 *   1. 記錄 ACK 訊息至 matt_message_log
 *   2. 更新對方的 lastHeartbeatTime 時間（若已為 connected 狀態）
 */
@Slf4j
@Component
public class S002AckHandler extends AbstractAckHandler<S002AckPayload> {

    private final MqttMessageLogService logService;
    private final MqttConnectionService connectionService;

    /**
     * 建構子
     *
     * @param objectMapper JSON 物件轉換器，用於反序列化與轉換 JsonNode
     * @param logService   封裝 MQTT 訊息記錄邏輯的服務（寫入 mqtt_message_log）
     * @param connectionService 管理連線狀態與更新心跳時間
     */
    public S002AckHandler(ObjectMapper objectMapper,
                          MqttMessageLogService logService,
                          MqttConnectionService connectionService) {
        super(objectMapper);
        this.logService = logService;
        this.connectionService = connectionService;
    }

    /**
     * 實際執行 ACK 訊息的處理流程。
     * <p>
     * 1. 印出日誌顯示收到的結果
     * 2. 將 payload 轉為 JsonNode
     * 3. 使用 logService 記錄 ACK 到資料庫
     * 4. 更新對方心跳時間（僅限 connected 狀態）
     *
     * @param system 發送方系統代碼（如 seec / ase）
     * @param topic  MQTT 原始 topic（如 seec_to_saa）
     * @param ack    已反序列化的 ACK payload 資料
     */
    @Override
    protected void process(String system, String topic, S002AckPayload ack) throws Exception {
        // 1️⃣ 印出日誌：顯示回覆來源與結果狀態
        log.info("[S002] 收到 ACK：result={}, topic={}, system={}",
                ack.getResult(),
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

        // 4️⃣ 嘗試更新對方的心跳時間（僅更新，不重設狀態）
        connectionService.refreshHeartbeat(system);
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 快取註冊與支援判斷。
     *
     * @return "S002"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S002";
    }

    /**
     * 回傳 payload 對應的物件型別（提供給 Jackson 用於反序列化）。
     *
     * @return S002AckPayload.class
     */
    @Override
    protected Class<S002AckPayload> getAckType() {
        return S002AckPayload.class;
    }
}
