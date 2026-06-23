package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S016AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S016CommandPayload;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.checktimer.CheckTimerSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * S016CommandHandler
 * - 負責處理 CMD_ID=S016 的指令（系統校時）
 * - 一般由 SAA 傳送 S016 指令至 SEEC，用於同步主系統時間
 * - 處理流程包含：
 *   1. 記錄 COMMAND 訊息至 mqtt_message_log
 *   2. 實際執行校時（依業務需求，實際同步時間）
 *   3. 回傳 ACK 給對方
 */
@Slf4j
@Component
public class S016CommandHandler extends AbstractCommandHandler<S016CommandPayload> {

    /** 封裝 MQTT 訊息記錄邏輯的服務（寫入 mqtt_message_log） */
    private final MqttMessageLogService logService;

    /** 提供本系統系統代碼（如 SAA/SEEC） */
    private final SystemContext systemContext;

    /** ZIP 指令服務（對 ZIPA / ZIPB 呼叫 WebAPI） */
    private final ZipStockerCommandService zipService;

    /**
     * 建構子
     *
     * @param objectMapper           JSON 處理器
     * @param responseEventPublisher Spring Event 發送器
     * @param logService             MQTT 訊息記錄服務
     * @param systemContext          系統識別（如 SAA/SEEC）
     * @param zipService             ZIP 指令服務（發送 CheckTimer 等）
     */
    public S016CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              ZipStockerCommandService zipService) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.zipService = zipService;
    }

    /**
     * 處理收到的 S016 指令
     * <p>
     * 1. 記錄校時 COMMAND 訊息
     * 2. 實際執行校時（yyyyMMddHHmmss 格式，Windows）
     * 3. 對 ZIPA 與 ZIPB 發送 CheckTimer
     * 4. 回覆 ACK 給對方
     *
     * @param system  來源系統（SAA/SEEC）
     * @param topic   MQTT topic（如 saa_to_seec）
     * @param command 已反序列化的 S016CommandPayload 物件
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, S016CommandPayload command, MqttMessageType type) throws Exception {
        // 1️⃣ 日誌顯示校時資訊
        log.info("[S016] 收到系統校時指令：TID={}, topic={}, system={}, datetime={}",
                command.getTid(),
                topic,
                system,
                command.getMessage() != null ? command.getMessage().getDatetime() : ""
        );

        // 2️⃣ 記錄 COMMAND 訊息
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                                 // sender：對方系統
                systemContext.getSystemCode(),          // receiver：本系統
                payload,
                MqttMessageType.COMMAND
        );

        // 3️⃣ 執行實際校時與組建 ACK payload
        String datetime = command.getMessage() != null ? command.getMessage().getDatetime() : null;
        S016AckPayload ack = new S016AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S016");
        ack.setIdDesc("SYSTEM_TIMING");
        ack.setTid(command.getTid());

        S016AckPayload.Message msg = new S016AckPayload.Message();
        msg.setDatetime(command.getMessage().getDatetime());

        ack.setMessage(msg);

        // 3.1) 先檢核字串格式
        if (datetime == null || !datetime.matches("\\d{14}")) {
            ack.setResult("FAIL");
            ack.setResultMessage("Datetime format error, require yyyyMMddHHmmss, input=" + datetime);
            // 直接回 ACK（不嘗試對 ZIP 校時，避免送出錯誤時間）
            publishAck(system, ack);
            return;
        }

        // 3.2) 將 yyyyMMddHHmmss 解析為 LocalDateTime
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        final LocalDateTime ldt;
        try {
            ldt = LocalDateTime.parse(datetime, formatter);
        } catch (Exception ex) {
            ack.setResult("FAIL");
            ack.setResultMessage("Datetime parse error: " + ex.getMessage());
            publishAck(system, ack);
            return;
        }

        // 3.3) 更新本機 Windows 系統日期/時間（需要管理員權限）
        boolean osOk;
        String osMsg;
        try {
            // 轉換為 Windows 指令格式
            String dateStr = String.format("%04d-%02d-%02d",
                    ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth());
            String timeStr = String.format("%02d:%02d:%02d",
                    ldt.getHour(), ldt.getMinute(), ldt.getSecond());
            // ※ Windows cmd `time` 只到秒，毫秒會被捨去

            // 執行 Windows 系統日期/時間設定（需要管理員權限）
            Process dateProcess = Runtime.getRuntime().exec("cmd /c date " + dateStr);
            int dateExit = dateProcess.waitFor();

            Process timeProcess = Runtime.getRuntime().exec("cmd /c time " + timeStr);
            int timeExit = timeProcess.waitFor();

            osOk = (dateExit == 0 && timeExit == 0);
            osMsg = osOk ? "OK" : ("Set system date/time failed. ExitCode: " + dateExit + "/" + timeExit);
            log.info("[S016] 本機系統時間設定結果: ok={}, msg={}", osOk, osMsg);
        } catch (Exception ex) {
            osOk = false;
            osMsg = "Exception: " + ex.getMessage();
            log.warn("[S016] 本機系統時間設定失敗: {}", ex.getMessage(), ex);
        }

        // 3.4) 無論 OS 成功與否，均嘗試發送 ZIPA／ZIPB 的 CheckTimer
        boolean zipAOk = false, zipBOk = false;
        String zipAMsg = "", zipBMsg = "";

        try {
            Root<CheckTimerSecondaryBody> rA = zipService.sendCheckTimer(
                    ZipTarget.ZIPA,
                    ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth(),
                    ldt.getHour(), ldt.getMinute(), ldt.getSecond());
            zipAOk = isZipOk(rA);
            zipAMsg = resultText(rA);
            log.info("[S016] ZIPA CheckTimer 結果 ok={}, msg={}", zipAOk, zipAMsg);
        } catch (Exception ex) {
            zipAOk = false;
            zipAMsg = "Exception: " + ex.getMessage();
            log.warn("[S016] ZIPA CheckTimer 發送失敗: {}", ex.getMessage(), ex);
        }

        try {
            Root<CheckTimerSecondaryBody> rB = zipService.sendCheckTimer(
                    ZipTarget.ZIPB,
                    ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth(),
                    ldt.getHour(), ldt.getMinute(), ldt.getSecond());
            zipBOk = isZipOk(rB);
            zipBMsg = resultText(rB);
            log.info("[S016] ZIPB CheckTimer 結果 ok={}, msg={}", zipBOk, zipBMsg);
        } catch (Exception ex) {
            zipBOk = false;
            zipBMsg = "Exception: " + ex.getMessage();
            log.warn("[S016] ZIPB CheckTimer 發送失敗: {}", ex.getMessage(), ex);
        }

        // 3.5) 聚合結果：本機 + ZIPA + ZIPB 都成功才 OK
        if (osOk && zipAOk && zipBOk) {
            ack.setResult("OK");
            ack.setResultMessage("");
        } else {
            ack.setResult("FAIL");
            // 保留三方的結果明細，方便對端判讀
            ack.setResultMessage(String.format("OS=%s; ZIPA=%s; ZIPB=%s",
                    osOk ? "OK" : osMsg,
                    zipAOk ? "OK" : zipAMsg,
                    zipBOk ? "OK" : zipBMsg));
        }

        // 4️⃣ 發送 ACK 訊息
        publishAck(system, ack);
    }

    /**
     * 發送 ACK 的封裝
     */
    private void publishAck(String system, S016AckPayload ack) throws Exception {
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /**
     * 判斷 ZIP Secondary 是否成功。
     * 依 ZIP 規格：Body.ResultInfo.Result == 0 表示成功
     */
    private boolean isZipOk(Root<CheckTimerSecondaryBody> resp) {
        if (resp == null || resp.getBody() == null) return false;

        CheckTimerSecondaryBody body = resp.getBody();
        CheckTimerSecondaryBody.ResultInfoWrapper ri = body.getResultInfo();
        if (ri == null) return false;

        // 0 = 成功；非 0 = 失敗
        return ri.getResult() == 0;
    }

    /**
     * 統一擷取 ZIP Secondary 的說明字串（log/ACK 會用到）
     * 依 ZIP 規格：Body.ResultInfo.Result / ResultMessage
     */
    private String resultText(Root<CheckTimerSecondaryBody> resp) {
        if (resp == null) return "null";
        if (resp.getBody() == null) return "No body";

        CheckTimerSecondaryBody.ResultInfoWrapper ri = resp.getBody().getResultInfo();
        if (ri == null) return "No ResultInfo";

        int code = ri.getResult();
        String msg = ri.getResultMessage();
        return String.format("Result=%d, Message=%s", code, msg == null ? "" : msg);
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     *
     * @return "S016"
     */
    @Override
    protected String getCmdIdInternal() {
        return "S016";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     *
     * @return S016CommandPayload.class
     */
    @Override
    protected Class<S016CommandPayload> getCommandType() {
        return S016CommandPayload.class;
    }
}
