package com.czkuo.rdf88701.application.mqtt.handler.command;


import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.U020AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.U020CommandPayload;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.DispatchOrder.DispatchOrderSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * U020CommandHandler
 * - 負責處理 CMD_ID=U020 的指令（Output WIP 架人員取貨請求）
 * - ASE 通知廠商指定批號 WIP 需亮燈
 * - 處理流程：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. [預留] 執行亮燈通知、批號驗證等邏輯
 *   3. 回傳 ACK（批號處理狀態）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class U020CommandHandler extends AbstractCommandHandler<U020CommandPayload> {

    /** MQTT 訊息記錄服務，負責寫入 mqtt_message_log */
    private final MqttMessageLogService logService;

    /** 系統識別（如 SAA/SEEC），由 context 提供 */
    private final SystemContext systemContext;

    private final ZipStockerCommandService zipCommandService;

    /**
     * 建構子
     *
     * @param objectMapper           JSON 處理器
     * @param responseEventPublisher Spring Event 發送器
     * @param logService             MQTT 訊息記錄服務
     * @param systemContext          系統識別 context
     */
    public U020CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              ZipStockerCommandService zipCommandService) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.zipCommandService = zipCommandService;
    }

    /**
     * 處理收到的 U020 指令
     * <p>
     * 1. 記錄取貨亮燈請求 COMMAND 至資料庫
     * 2. [預留] 執行亮燈、批號驗證等業務
     * 3. 回覆 ACK
     *
     * @param system  來源系統（如 ASE）
     * @param topic   MQTT topic
     * @param command 已反序列化的 U020CommandPayload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, U020CommandPayload command, MqttMessageType type) throws Exception {
        List<U020CommandPayload.Message.LotInfo> lotList =
                (command.getMessage() != null) ? command.getMessage().getLotList() : null;
        int count = (lotList != null) ? lotList.size() : 0;
        log.info("[U020] 收到 Output WIP 架人員取貨請求：TID={}, topic={}, system={}, 批號數={}",
                command.getTid(), topic, system, count);

        // 1) 記錄 COMMAND 至 mqtt_message_log
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                        // sender
                systemContext.getSystemCode(), // receiver（本系統）
                payload,
                MqttMessageType.COMMAND
        );

        // 2) 解析 LOT_LIST → magazines（去空白、去重、保序）
        Set<String> magazines = new LinkedHashSet<>();
        if (lotList != null) {
            for (U020CommandPayload.Message.LotInfo lot : lotList) {
                if (lot != null && lot.getLotId() != null && !lot.getLotId().isBlank()) {
                    magazines.add(lot.getLotId().trim());
                }
            }
        }
        if (magazines.isEmpty()) {
            U020AckPayload ack = new U020AckPayload();
            ack.setCmd("UNLOAD");
            ack.setCmdId("U020");
            ack.setTid(command.getTid());
            ack.setIdDesc("OUTPUT_WIP_GET_TRAY");
            ack.setMessage(new U020AckPayload.Message());
            ack.setResult("FAIL");
            ack.setResultMessage("LOT_LIST is empty");

            String ackJson = objectMapper.writeValueAsString(ack);
            responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
            return;
        }

        // 3) 發 ZIP：DispatchOrder（一次丟多個 Magazines）
        boolean ok = true;
        String errMsg = "";
        Root<DispatchOrderSecondaryBody> resp = null;
        try {
            log.info("[U020] DispatchOrder → magazines={}", magazines);
            resp = zipCommandService.sendDispatchOrder(ZipTarget.ZIPB, new ArrayList<>(magazines), null);
            // 你若有 Secondary 的成功碼，可在這裡判斷並設定 ok / errMsg
        } catch (Exception e) {
            ok = false;
            errMsg = "DispatchOrder failed: " + e.getMessage();
            log.error("[U020] DispatchOrder exception", e);
        }

        // 4) 組 ACK
        U020AckPayload ack = new U020AckPayload();
        ack.setCmd("UNLOAD");
        ack.setCmdId("U020");
        ack.setTid(command.getTid());
        ack.setIdDesc("OUTPUT_WIP_GET_TRAY");

        U020AckPayload.Message ackMsg = new U020AckPayload.Message();
        if (lotList != null) {
            ackMsg.setLotList(
                    lotList.stream().map(lot -> {
                        U020AckPayload.Message.LotInfo info = new U020AckPayload.Message.LotInfo();
                        info.setLotId(lot != null ? lot.getLotId() : null);
                        return info;
                    }).toList()
            );
        }
        ack.setMessage(ackMsg);
        ack.setResult(ok ? "OK" : "FAIL");
        ack.setResultMessage(ok ? "" : errMsg);

        // 5) 發送 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());

        // 附帶記錄 Secondary（可選）
        if (resp != null) {
            log.info("[U020] DispatchOrder secondary header: {}", resp.getHeader());
        }
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "U020"
     */
    @Override
    protected String getCmdIdInternal() {
        return "U020";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return U020CommandPayload.class
     */
    @Override
    protected Class<U020CommandPayload> getCommandType() {
        return U020CommandPayload.class;
    }
}
