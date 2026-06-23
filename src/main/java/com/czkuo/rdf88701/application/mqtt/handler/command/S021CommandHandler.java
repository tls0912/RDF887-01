package com.czkuo.rdf88701.application.mqtt.handler.command;


import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.dto.DeviceProcessState;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S021AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S021CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;


/**
 * S021CommandHandler
 * ------------------------------------------------------------
 * - 處理 CMD_ID = S021 的指令（詢問系統處理狀態）
 * - 指令由 ASE 主動送出，詢問特定設備的當前運作狀態
 * - MESSAGE 區塊內可以帶 DEVICE_NAME（WIP / SPLIT / ZIPA / ZIPB）
 *   - 若未指定，預設查詢 WIP
 * - 狀態資料來源：
 *   - 由 ProcessStateMonitor 定時輪詢 PLC / ZIP API
 *   - 結果寫入 DeviceProcessStateCache
 *   - Handler 僅需讀快取，不直接打設備，避免阻塞
 *
 * 流程：
 *   1. 紀錄收到的 COMMAND 至 mqtt_message_log
 *   2. 判斷要查詢的設備名稱（預設 WIP）
 *   3. 從快取讀取設備狀態（優先新鮮資料）
 *   4. 組建 ACK payload 回覆 ASE
 *   5. 送出 ACK
 */
@Slf4j
@Component
public class S021CommandHandler extends AbstractCommandHandler<S021CommandPayload> {

    /** 訊息記錄服務，用於寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 系統代碼（例如 SAA/SEEC），由 SystemContext 提供 */
    private final SystemContext systemContext;

    /** 狀態讀取器，從快取取設備狀態 */
    private final DeviceProcessStateReader stateReader;

    /**
     * 建構子注入
     */
    public S021CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              DeviceProcessStateReader stateReader) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.stateReader = stateReader;
    }

    /**
     * 處理收到的 S021 指令
     *
     * @param system  來源系統（例如 ASE）
     * @param topic   MQTT topic
     * @param command 已反序列化的 S021CommandPayload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic,
                           S021CommandPayload command, MqttMessageType type) throws Exception {

        log.info("[S021] 收到設備狀態查詢：TID={}, topic={}, from={}", command.getTid(), topic, system);

        // 1) 紀錄 COMMAND
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,
                systemContext.getSystemCode(),
                payload,
                MqttMessageType.COMMAND
        );

        // 2) 解析查詢清單
        //    - MESSAGE 為空 -> 預設 WIP
        //    - 含 "*" -> 查詢全部機台
        final Set<String> targets = new LinkedHashSet<>();
        boolean hasWildcard = false;

        if (command.getMessage() != null) {
            for (S021CommandPayload.Message m : command.getMessage()) {
                if (m == null || m.getDeviceName() == null) continue;
                String name = m.getDeviceName().trim();
                if (name.isEmpty()) continue;
                if ("*".equals(name)) {
                    hasWildcard = true;
                } else {
                    targets.add(name.toUpperCase());
                }
            }
        }

        if (hasWildcard) {
            // 從 stateReader 取全部機台；若未提供 API，就用預設清單後續維護
            Collection<String> allNames = getAllDeviceNamesSafe();
            targets.clear();
            targets.addAll(allNames.stream().map(String::toUpperCase).toList());
        } else if (targets.isEmpty()) {
            targets.add("WIP");
        }

        // 3) 查詢狀態（先 fresh 再 fallback）
        List<S021AckPayload.Message> messages = new ArrayList<>(targets.size());
        boolean allFresh = true;

        for (String deviceName : targets) {
            Optional<DeviceProcessState> freshOpt = stateReader.getFresh(deviceName);
            DeviceProcessState st = freshOpt.orElseGet(() -> stateReader.getBestEffort(deviceName));
            boolean fresh = freshOpt.isPresent();
            if (!fresh) allFresh = false;

            S021AckPayload.Message item = new S021AckPayload.Message();
            item.setDeviceName(st.getDeviceName());
            item.setStatus(st.getStatus().name());
            messages.add(item);
        }

        // 4) 組 ACK
        S021AckPayload ack = new S021AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S021");
        ack.setTid(command.getTid());
        ack.setIdDesc("SYSTEM_PROCESS_STATUS");
        ack.setMessage(messages);
        ack.setResult("OK");
        ack.setResultMessage("");

        // 5) 發送 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(
                system,
                ackJson,
                MqttMessageType.ACK,
                ack.getTid(),
                ack.getCmdId()
        );

        log.info("[S021] 已回覆 {} 筆狀態（{}）", messages.size(), ack.getResultMessage());
    }

    /**
     * 取得全部機台名稱
     */
    private Collection<String> getAllDeviceNamesSafe() {
        return List.of("WIP", "拆併區", "ZIPA", "ZIPB");
    }

    /** 回傳對應的 CMD_ID，供 Router 註冊與分派 */
    @Override
    protected String getCmdIdInternal() {
        return "S021";
    }

    /** 回傳 payload 型別，供 Jackson 反序列化 */
    @Override
    protected Class<S021CommandPayload> getCommandType() {
        return S021CommandPayload.class;
    }
}
