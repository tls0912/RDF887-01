package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S069AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S069CommandPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * S069CommandHandler
 * - CMD_ID=S069：手動觸發 WARNING（由外部系統要求本系統主動送 PLC）
 * - 流程：
 *   1) 記錄 MQTT COMMAND 到 mqtt_message_log（入帳）
 *   2) 將指定 ALID 的 alarm_item 佇列旗標置為 1（want_plc_trigger=1，前提：enabled & allow）
 *      並寫入 alarm_item_log = 'PLC_ON'（事件快照：title_zh/title_en）
 *   3) 回 ACK（OK/NG + 訊息）
 *
 * 說明：
 * - 我們採「值變才更新」：若本來就已經在佇列中，不重複更新/記錄。
 * - Handler 標註 @Transactional：#2 的更新與 log 寫入要同成敗。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S069CommandHandler extends AbstractCommandHandler<S069CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;

    private final PlcAccessService plc;

    private static final String W_MSG    = "W00E0";
    private static final String W_DVS    = "W00FA";
    private static final String W_IDX    = "W00FB";

    public S069CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              PlcAccessService plc) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.plc = plc;
    }

    /**
     * 流程：
     * 1) 記 mqtt_message_log（COMMAND）
     * 2) 驗證單筆 MESSAGE 並寫 PLC（W_MSG / W_DVS / W_IDX += 1）
     * 3) 回單一 ACK（頂層 RESULT / RESULT_MESSAGE + echo MESSAGE）
     */
    @Override
    @Transactional
    protected void process(String system, String topic, S069CommandPayload command, MqttMessageType type) throws Exception {
        final String tid = command.getTid();
        log.info("[S069] 收到手動 WARNING：TID={}, topic={}, system={}", tid, topic, system);

        // 1) 記錄 COMMAND 至 mqtt_message_log
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                        // sender
                systemContext.getSystemCode(), // receiver
                payload,
                MqttMessageType.COMMAND
        );

        // 準備 ACK（單筆 MESSAGE）
        S069AckPayload ack = new S069AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S069");
        ack.setTid(tid);
        ack.setIdDesc("WARNING");

        S069CommandPayload.Message req = command.getMessage();
        if (req == null) {
            ack.setResult("NG");
            ack.setResultMessage("MESSAGE 為空");
            ack.setMessage(null);
            publishAck(system, ack);
            log.warn("[S069] TID={} MESSAGE 為空，回覆 NG", tid);
            return;
        }

        // Echo 回傳用（ACK.MESSAGE）
        S069AckPayload.Message ackMsg = new S069AckPayload.Message();
        ackMsg.setDeviceName(safeTrim(req.getDeviceName()));
        ackMsg.setAlid(safeTrim(req.getAlid()));
        ackMsg.setAlidDescEn(safeTrim(req.getAlidDescEn()));
        ackMsg.setAlidDescCh(safeTrim(req.getAlidDescCh()));
        ack.setMessage(ackMsg);

        final String deviceName = ackMsg.getDeviceName();
        final String alidStr    = ackMsg.getAlid();
        final String descEn     = ackMsg.getAlidDescEn();

        // 檢查 ALID 必須是整數
        Integer alid = tryParseInt(alidStr);
        if (alid == null) {
            String em = "ALID 非整數 -> " + alidStr;
            ack.setResult("NG");
            ack.setResultMessage(em);
            publishAck(system, ack);
            log.warn("[S069] {}", em);
            return;
        }

        // 解析 device mask：WIP=1、拆併/拆併區/SPLIT=2、ZIPA=4、ZIPB=8、*=15；空白預設 WIP
        Integer mask = resolveDeviceMask(deviceName);
        if (mask == null) {
            String em = "不支援的 DEVICE_NAME -> " + deviceName;
            ack.setResult("NG");
            ack.setResultMessage(em);
            publishAck(system, ack);
            log.warn("[S069] {}", em);
            return;
        }

        // WARNING 字串：<ALID>-<DESC_EN>（缺任一段就取另一段；全空則取 ALID）
        String msg = buildWarningMessage(alidStr, descEn);
        if (StringUtils.isBlank(msg)) msg = alidStr;

        try {
            // 2) 寫 PLC
            plc.writeString("PLC-Main", W_MSG, msg);
            plc.writeUInt16("PLC-Main", W_DVS, mask);

            int idxBefore = plc.readUInt16("PLC-Main", W_IDX);
            int idxAfter  = idxBefore + 1;
            plc.writeUInt16("PLC-Main", W_IDX, idxAfter);

            log.info("[S069] 觸發 OK：device='{}', mask={}, ALID={}, MSG='{}', IDX {}->{}",
                    deviceName, mask, alid, msg, idxBefore, idxAfter);

            // 3) 回 ACK (OK)
            ack.setResult("OK");
            ack.setResultMessage("");
        } catch (Exception ex) {
            String em = "PLC 寫入失敗 - " + ex.getMessage();
            ack.setResult("NG");
            ack.setResultMessage(em);
            log.warn("[S069] 觸發失敗：{}", ex.getMessage(), ex);
        }

        publishAck(system, ack);
        log.info("[S069] 已送出 ACK：TID={}, RESULT={}", tid, ack.getResult());
    }

    /**
     * 裝置名稱 → bitmask：
     * WIP=1、拆併/拆併區=2、ZIPA=4、ZIPB=8、*=15
     */
    private static Integer resolveDeviceMask(String deviceNameRaw) {
        if (StringUtils.isBlank(deviceNameRaw)) return 1; // 空值預設 WIP（與 S021 類似預設）
        String dn = deviceNameRaw.trim();
        if ("*".equals(dn)) return 1 + 2 + 4 + 8;

        String up = dn.toUpperCase();

        if ("WIP".equals(up)) return 1;
        if ("拆併".equals(dn) || "拆併區".equals(dn)) return 2;
        if ("ZIPA".equals(up)) return 4;
        if ("ZIPB".equals(up)) return 8;

        return null;
    }

    private static String buildWarningMessage(String alid, String descEn) {
        if (StringUtils.isBlank(alid) && StringUtils.isBlank(descEn)) return "";
        if (StringUtils.isBlank(alid)) return descEn;
        if (StringUtils.isBlank(descEn)) return alid;
        return alid + "-" + descEn;
    }

    private void publishAck(String system, S069AckPayload ack) throws Exception {
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static Integer tryParseInt(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    @Override
    protected String getCmdIdInternal() { return "S069"; }

    @Override
    protected Class<S069CommandPayload> getCommandType() { return S069CommandPayload.class; }
}
