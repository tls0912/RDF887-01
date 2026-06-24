package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S014AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S014CommandPayload;
import com.czkuo.rdf88701.domain.repository.ToolCatalogRepository;
import com.czkuo.rdf88701.domain.repository.ToolLimitOverrideRepository;
import com.czkuo.rdf88701.domain.repository.ToolStatusRepository;
import com.czkuo.rdf88701.infra.entity.ToolCatalog;
import com.czkuo.rdf88701.infra.entity.ToolLimitOverride;
import com.czkuo.rdf88701.infra.entity.ToolStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * S014CommandHandler
 * - 處理 CMD_ID=S014 的指令（零件預警清單）
 * - 流程說明：
 *   1. 記錄原始訊息（COMMAND）
 *   2. 回傳 ACK（零件清單、狀態等資訊）
 *   3. 記錄所有過程於 logService
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S014CommandHandler extends AbstractCommandHandler<S014CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;
    private final ToolCatalogRepository toolCatalogRepo;
    private final ToolLimitOverrideRepository toolLimitOverrideRepo;
    private final ToolStatusRepository toolStatusRepo;

    /**
     * 建構子
     *
     * @param objectMapper           JSON 物件轉換器
     * @param responseEventPublisher Spring Event 方式封裝訊息發送
     * @param logService             MQTT 訊息記錄服務
     * @param systemContext          本系統識別資訊
     * @param toolCatalogRepo        工具目錄 Repository
     * @param toolLimitOverrideRepo  工具上限覆寫 Repository
     * @param toolStatusRepo         工具狀態 Repository
     */
    public S014CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              ToolCatalogRepository toolCatalogRepo,
                              ToolLimitOverrideRepository toolLimitOverrideRepo,
                              ToolStatusRepository toolStatusRepo) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.toolCatalogRepo = toolCatalogRepo;
        this.toolLimitOverrideRepo = toolLimitOverrideRepo;
        this.toolStatusRepo = toolStatusRepo;
    }

    /**
     * 處理 S014 指令（零件預警清單）
     *
     * @param system  來源系統（ASE/SEEC/等）
     * @param topic   原始 MQTT topic
     * @param command 已反序列化的指令 payload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, S014CommandPayload command, MqttMessageType type) throws Exception {
        // 1️⃣ 日誌記錄收到的訊息
        log.info("[S014] 收到零件預警清單指令：TID={}, topic={}, system={}", command.getTid(), topic, system);

        // 2️⃣ 記錄原始指令（COMMAND）至資料庫
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                                 // sender：對方系統
                systemContext.getSystemCode(),          // receiver：本系統
                payload,                                // payload
                MqttMessageType.COMMAND
        );

        // 3️⃣ 查詢工具清單 → 整合三張表：catalog + override + status
        List<ToolCatalog> catalogs = toolCatalogRepo.findAll();
        Map<String, ToolLimitOverride> overrides = toolLimitOverrideRepo.findAll()
                .stream().collect(Collectors.toMap(ToolLimitOverride::getToolName, Function.identity(), (a, b)->b));
        Map<String, ToolStatus> statuses = toolStatusRepo.findAll()
                .stream().collect(Collectors.toMap(ToolStatus::getToolName, Function.identity(), (a, b)->b));

        List<S014AckPayload.ToolInfo> toolList = catalogs.stream()
                .sorted(Comparator.comparing(ToolCatalog::getToolName))
                .map(c -> {
                    ToolLimitOverride o = overrides.get(c.getToolName());
                    ToolStatus s = statuses.get(c.getToolName());

                    S014AckPayload.ToolInfo t = new S014AckPayload.ToolInfo();
                    t.setToolName(c.getToolName());
                    t.setCurrentStatus(s != null && s.getCurrentValue() != null ? s.getCurrentValue() : "N/A");
                    t.setToolLimit(o != null && o.getOverrideLimit() != null ? o.getOverrideLimit() : c.getDefaultLimit());
                    t.setUnit(o != null && o.getUnit() != null ? o.getUnit() : c.getUnit());
                    return t;
                })
                .toList();

        // 4️⃣ 組出 ACK payload
        S014AckPayload ack = new S014AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S014");
        ack.setIdDesc("TOOL_REMIND_LIST");
        ack.setTid(command.getTid());
        ack.setResult("OK");
        ack.setResultMessage(""); // 預設無錯誤

        S014AckPayload.Message ackMessage = new S014AckPayload.Message();
        ackMessage.setToolList(toolList);
        ack.setMessage(ackMessage);

        // 5️⃣ 發送 ACK 給對方
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /** 回傳對應的 CMD_ID */
    @Override
    protected String getCmdIdInternal() {
        return "S014";
    }

    /** 回傳 payload 類型（for 反序列化） */
    @Override
    protected Class<S014CommandPayload> getCommandType() {
        return S014CommandPayload.class;
    }
}
