package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S015AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S015CommandPayload;
import com.czkuo.rdf88701.domain.repository.ToolCatalogRepository;
import com.czkuo.rdf88701.domain.repository.ToolLimitOverrideRepository;
import com.czkuo.rdf88701.infra.entity.ToolCatalog;
import com.czkuo.rdf88701.infra.entity.ToolLimitOverride;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * S015CommandHandler
 * - 處理 CMD_ID=S015 的指令（零件預警設定）
 * - 收到對方零件預警設定指令時，執行下列流程：
 *   1. 記錄原始 COMMAND 訊息
 *   2. 回傳 ACK 給對方
 *   3. 執行業務邏輯：如解析與儲存 TOOL_LIST 設定（預留擴充點）
 */
@Slf4j
@Component
public class S015CommandHandler extends AbstractCommandHandler<S015CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;

    private final ToolLimitOverrideRepository toolLimitOverrideRepo;
    private final ToolCatalogRepository toolCatalogRepo; // （可選）作為名稱白名單檢核

    /**
     * 建構子
     *
     * @param objectMapper           JSON 處理器
     * @param responseEventPublisher Spring Event 發送器
     * @param logService             MQTT 訊息記錄服務
     * @param systemContext          系統識別（如 SAA/SEEC）
     */
    public S015CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              ToolLimitOverrideRepository toolLimitOverrideRepo,
                              ToolCatalogRepository toolCatalogRepo) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.toolLimitOverrideRepo = toolLimitOverrideRepo;
        this.toolCatalogRepo = toolCatalogRepo;
    }

    /**
     * 處理 S015 指令
     *
     * @param system  來源系統（如 SAA/SEEC）
     * @param topic   原始 MQTT topic
     * @param command 已反序列化的 S015CommandPayload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, S015CommandPayload command, MqttMessageType type) throws Exception {
        // 1️⃣ 記錄收到的訊息資訊
        log.info("[S015] 收到零件預警設定指令：TID={}, topic={}, system={}, toolCount={}",
                command.getTid(), topic, system,
                command.getMessage() != null && command.getMessage().getToolList() != null
                        ? command.getMessage().getToolList().size() : 0);

        // 2️⃣ 記錄原始 COMMAND 訊息
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                                 // sender（對方系統）
                systemContext.getSystemCode(),          // receiver（本系統）
                payload,
                MqttMessageType.COMMAND
        );

        // 3️⃣ [執行業務] 解析與儲存 TOOL_LIST 設定（最小可行：覆寫上限 UPSERT）
        String result = "OK";
        String resultMsg = "";

        try {
            // 3.1 取 payload 列表（可能為 null）
            List<S015CommandPayload.ToolSetting> items =
                    command.getMessage() != null && command.getMessage().getToolList() != null
                            ? command.getMessage().getToolList()
                            : Collections.emptyList();

            // 3.2 先把現有 override 與 catalog 全撈，做成 Map 加速查找
            Map<String, ToolLimitOverride> existingOverrides = toolLimitOverrideRepo.findAll()
                    .stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(ToolLimitOverride::getToolName, Function.identity(), (a, b)->b));

            // （可選）白名單：只允許 catalog 中存在的名稱
            Set<String> catalogNames = toolCatalogRepo.findAll()
                    .stream()
                    .map(ToolCatalog::getToolName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // 3.3 逐筆套用（全部成功才算 OK）
            int ok = 0, fail = 0;
            StringBuilder errs = new StringBuilder();

            for (S015CommandPayload.ToolSetting it : items) {
                String name  = safe(it.getToolName());
                String limit = safe(it.getToolLimit());
                String unit  = safe(it.getUnit());

                // 極簡驗證（必要欄位）
                boolean bad = name.isBlank() || limit.isBlank() || unit.isBlank();

                // （可選）開啟白名單檢核：如需嚴格比對 uncomment 下一行
                // if (!catalogNames.contains(name)) { bad = true; errs.append("unknown name: ").append(name).append("; "); }

                if (bad) {
                    fail++;
                    if (name.isBlank()) name = "(blank)";
                    errs.append("bad item: ").append(name).append("; ");
                    continue;
                }

                ToolLimitOverride exist = existingOverrides.get(name);
                if (exist == null) {
                    // INSERT
                    ToolLimitOverride e = new ToolLimitOverride();
                    e.setToolName(name);
                    e.setOverrideLimit(limit);
                    e.setUnit(unit);
                    // 若你的 Entity 有 isActive 欄位，確保預設為 1；沒有就略過
                    try {
                        boolean okInserted = toolLimitOverrideRepo.save(e);
                        if (okInserted) {
                            ok++;
                            existingOverrides.put(name, e);
                        } else {
                            fail++; errs.append("insert fail: ").append(name).append("; ");
                        }
                    } catch (Exception ex) {
                        fail++; errs.append("insert ex: ").append(name).append(" -> ").append(ex.getMessage()).append("; ");
                    }
                } else {
                    // UPDATE（以 id 更新；你的 Repo 只有 updateById -> 我們設值後 update）
                    exist.setOverrideLimit(limit);
                    exist.setUnit(unit);
                    try {
                        boolean okUpdated = toolLimitOverrideRepo.update(exist);
                        if (okUpdated) {
                            ok++;
                        } else {
                            fail++; errs.append("update fail: ").append(name).append("; ");
                        }
                    } catch (Exception ex) {
                        fail++; errs.append("update ex: ").append(name).append(" -> ").append(ex.getMessage()).append("; ");
                    }
                }
            }

            if (fail > 0) {
                result = "FAIL";
                resultMsg = "applied=" + ok + ", failed=" + fail + ", " + errs;
            } else {
                result = "OK";
                resultMsg = "applied=" + ok;
            }

        } catch (Exception ex) {
            // 若整體發生例外，回 FAIL 並附訊息
            result = "FAIL";
            resultMsg = ex.getMessage() != null ? ex.getMessage() : "apply settings error";
            log.warn("[S015] apply settings failed: {}", resultMsg, ex);
        }

        // 4️⃣ 組建 ACK payload
        S015AckPayload ack = new S015AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S015");
        ack.setIdDesc("TOOL_REMIND_SETTING");
        ack.setTid(command.getTid());
        ack.setResult(result);           // 實際可依執行結果設為 "OK" 或 "FAIL"
        ack.setResultMessage(resultMsg); // 可填入失敗原因或其他說明

        // 5️⃣ 發送 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /** 回傳 CMD_ID，供 Router 註冊 */
    @Override
    protected String getCmdIdInternal() {
        return "S015";
    }

    /** 回傳 payload 型別，提供給 Jackson 反序列化 */
    @Override
    protected Class<S015CommandPayload> getCommandType() {
        return S015CommandPayload.class;
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
}
