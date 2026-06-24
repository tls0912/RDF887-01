package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S045AckPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.command.S045CommandPayload;
import com.czkuo.rdf88701.domain.repository.SafetyPointRepository;
import com.czkuo.rdf88701.domain.repository.SafetyStatusSnapshotRepository;
import com.czkuo.rdf88701.infra.entity.SafetyPoint;
import com.czkuo.rdf88701.infra.entity.SafetyStatusSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * S045CommandHandler
 * - CMD_ID=S045（安全 Sensor 狀態請求）
 * - 流程：
 *   1) 記錄 COMMAND 至 mqtt_message_log
 *   2) 查詢 DB：所有啟用中的 safety_point（enabled='Y'），對應 safety_status_snapshot
 *   3) 回 ACK：
 *        DEVICE_NAME = point_name
 *        DEVICE_STATUS = 觸發回 "NG"、正常回 "OK"
 *        STATUS_DESCRIPTION = remark（若空則 "type_code addr_expr"）並追加 "（被觸發）"/"（正常）"
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S045CommandHandler extends AbstractCommandHandler<S045CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;
    private final SafetyPointRepository safetyPointRepository;
    private final SafetyStatusSnapshotRepository safetyStatusSnapshotRepository;

    public S045CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              SafetyPointRepository safetyPointRepository,
                              SafetyStatusSnapshotRepository safetyStatusSnapshotRepository) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.safetyPointRepository = safetyPointRepository;
        this.safetyStatusSnapshotRepository = safetyStatusSnapshotRepository;
    }

    @Override
    protected void process(String system, String topic, S045CommandPayload command, MqttMessageType messageType) throws Exception {
        // 1) 記錄收到的指令
        log.info("[S045] 收到安全 Sensor 狀態請求：TID={}, topic={}, system={}",
                command.getTid(), topic, system);

        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                                // sender（對方系統）
                systemContext.getSystemCode(),         // receiver（本系統）
                payload,
                MqttMessageType.COMMAND
        );

        // 2) 撈啟用中的點位
        List<SafetyPoint> enabledPoints = safetyPointRepository.findAllEnabled();

        // 2-1) 將 snapshot 做成 Map（pointId -> snapshot），避免 N+1
        Map<Long, SafetyStatusSnapshot> snapshotByPointId = new HashMap<>();
        for (SafetyStatusSnapshot s : safetyStatusSnapshotRepository.findAll()) {
            snapshotByPointId.put(s.getPointId(), s);
        }

        // 3) 映射到 ACK 的 SAFETY_DEVICE_LIST
        // 觸發 => NG；正常 => OK；描述追加（被觸發） / （正常）
        List<S045AckPayload.SafetyDeviceStatus> deviceList = enabledPoints.stream()
                .map(p -> {
                    S045AckPayload.SafetyDeviceStatus d = new S045AckPayload.SafetyDeviceStatus();

                    // DEVICE_NAME = 點位名稱（顯示友善）
                    d.setDeviceName(safe(p.getPointName()));

                    // 取 snapshot 判斷是否觸發（預設視為未觸發 = 正常）
                    SafetyStatusSnapshot snap = snapshotByPointId.get(p.getId());
                    boolean triggered = snap != null && "Y".equalsIgnoreCase(safe(snap.getIsTriggered()));

                    // 觸發 -> NG；正常 -> OK
                    d.setDeviceStatus(triggered ? "NG" : "OK");

                    // 基礎描述：優先 remark；空則 fallback = "type_code addr_expr"
                    String baseDesc = trimOrEmpty(p.getRemark());
                    if (baseDesc.isEmpty()) {
                        String typeCode = trimOrEmpty(p.getTypeCode());
                        String addrExpr = trimOrEmpty(p.getAddrExpr());
                        baseDesc = (typeCode.isEmpty() && addrExpr.isEmpty())
                                ? ""
                                : (typeCode + (typeCode.isEmpty() || addrExpr.isEmpty() ? "" : " ") + addrExpr);
                    }

                    // 追加狀態文字（被觸發/正常）
                    String statusLabel = triggered ? "（被觸發）" : "（正常）";
                    String finalDesc = baseDesc.isEmpty() ? statusLabel : (baseDesc + statusLabel);

                    d.setStatusDescription(finalDesc);
                    return d;
                })
                .collect(Collectors.toList());

        // 4) 組 ACK
        S045AckPayload.Message ackMsg = new S045AckPayload.Message();
        ackMsg.setSafetyDeviceList(deviceList);

        S045AckPayload ack = new S045AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S045");
        ack.setTid(command.getTid());
        ack.setIdDesc("SAFETY_DEVICE_STATUS");
        ack.setResult("OK");
        ack.setResultMessage("");
        ack.setMessage(ackMsg);

        // 5) 發送 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());

        log.info("[S045] 回覆安全 Sensor 狀態 ACK：count={}, TID={}", deviceList.size(), ack.getTid());
    }

    @Override
    protected String getCmdIdInternal() {
        return "S045";
    }

    @Override
    protected Class<S045CommandPayload> getCommandType() {
        return S045CommandPayload.class;
    }

    /* ===== 工具：null/trim 處理 ===== */

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
