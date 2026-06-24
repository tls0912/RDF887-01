package com.czkuo.rdf88701.application.mqtt.handler.command;


import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S044AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S044CommandPayload;
import com.czkuo.rdf88701.domain.repository.SafetyDeviceTypeRepository;
import com.czkuo.rdf88701.domain.repository.SafetyPointRepository;
import com.czkuo.rdf88701.infra.entity.SafetyDeviceType;
import com.czkuo.rdf88701.infra.entity.SafetyPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * S044CommandHandler
 * <p>
 * - 負責處理 CMD_ID = S044 的「查詢安全 Sensor 清單」命令（COMMAND）。
 * - 收到指令後，會：
 *   1) 把該 COMMAND 記錄到 mqtt_message_log
 *   2) 從資料庫表 safety_point 撈出「啟用(enabled = 'Y')」的點位
 *   3) 依 group_word／bit_hex 做排序（group_word 去掉首字母 W 再當十六進位排序；bit_hex 0..F 十六進位排序）
 *   4) 組裝回覆的 ACK（S044AckPayload），其中：
 *        - DEVICE_NAME        = safety_point.point_name
 *        - DEVICE_DESCRIPTION = safety_point.remark（若為空字串，則以 "type_code addr_expr" 補上）
 *   5) 透過 MqttMessageEventPublisher 發回 ACK
 *
 * 備註：
 * - 這裡將「一個啟用點位」對應成 ACK 中 SAFETY_DEVICE_LIST 的「一筆項目」，
 *   若未來想以「類別(EMO/DOOR/… )」彙整成少數幾筆，改組裝對應的邏輯即可。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S044CommandHandler extends AbstractCommandHandler<S044CommandPayload> {

    /** 寫入 mqtt_message_log 用的服務 */
    private final MqttMessageLogService logService;

    /** 用來取得本系統代碼（例如 SAA/SEEC）的 context */
    private final SystemContext systemContext;

    /** 安全點位資料存取介面（撈 safety_point 用） */
    private final SafetyPointRepository safetyPointRepository;

    /**
     * 建構子
     *
     * @param objectMapper            Jackson 物件映射器
     * @param responseEventPublisher  封裝發送 MQTT 回覆（透過 Spring Event）
     * @param logService              訊息記錄服務（寫 mqtt_message_log）
     * @param systemContext           系統識別（我方系統代碼）
     * @param safetyPointRepository   資料庫存取 safety_point
     */
    public S044CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              SafetyPointRepository safetyPointRepository) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.safetyPointRepository = safetyPointRepository;
    }

    /**
     * 處理 S044 COMMAND：
     * 1) 記錄 COMMAND 到 DB
     * 2) 撈出啟用中的安全點位，排序並轉成 SAFETY_DEVICE_LIST
     * 3) 回傳 ACK（OK/EMPTY）
     *
     * @param system  來源系統（例如 ASE）
     * @param topic   MQTT Topic
     * @param command 已反序列化的指令 payload
     * @param type    訊息類型（COMMAND）
     */
    @Override
    protected void process(String system, String topic, S044CommandPayload command, MqttMessageType type) throws Exception {
        // === 1) 記錄 COMMAND 到 mqtt_message_log（以便追蹤） ===
        log.info("[S044] 收到安全 Sensor 清單查詢：TID={}, topic={}, from={}", command.getTid(), topic, system);
        JsonNode json = objectMapper.valueToTree(command);
        logService.record(
                topic,                         // topic
                system,                        // sender = 來源系統
                systemContext.getSystemCode(), // receiver = 我方系統
                json,                          // 原始 payload
                MqttMessageType.COMMAND        // 類型 = COMMAND
        );

        // === 2) 撈啟用點位，並做排序（group_word/bit_hex 十六進位順序） ===
        List<SafetyPoint> enabledPoints = safetyPointRepository.findAllEnabled();

        // 排序規則：
        // - group_word 形如 "W1044"：去掉首字母(W)，把 "1044" 轉十六進位數字排序
        // - bit_hex     形如 "A"    ：以十六進位數字排序
        enabledPoints.sort(Comparator
                .comparingInt((SafetyPoint p) -> parseHexSafe(stripPrefixW(nullToEmpty(p.getGroupWord()))))
                .thenComparingInt((SafetyPoint p) -> parseHexSafe(nullToEmpty(p.getBitHex())))
        );

        // === 3) 映射為 SAFETY_DEVICE_LIST ===
        // DEVICE_NAME        = point_name
        // DEVICE_DESCRIPTION = remark（若空字串則 fallback "type_code addr_expr"）
        List<S044AckPayload.SafetyDevice> deviceList = enabledPoints.stream()
                .map((SafetyPoint p) -> {
                    S044AckPayload.SafetyDevice d = new S044AckPayload.SafetyDevice();

                    // 裝置名稱 = 點位名稱（顯示友善），null 安全處理
                    d.setDeviceName(nullToEmpty(p.getPointName()));

                    // 先拿 remark，若 remark 为空則 fallback
                    String desc = trimOrEmpty(p.getRemark());
                    if (desc.isEmpty()) {
                        String typeCode = trimOrEmpty(p.getTypeCode());   // ← 改名，不要叫 `type`
                        String addrExpr = trimOrEmpty(p.getAddrExpr());
                        // 兩個都空就保持空，否則以「typeCode + 空格 + addrExpr」組合（其中任一空就不加空格）
                        desc = (typeCode.isEmpty() && addrExpr.isEmpty())
                                ? ""
                                : (typeCode + (typeCode.isEmpty() || addrExpr.isEmpty() ? "" : " ") + addrExpr);
                    }

                    d.setDeviceDescription(desc);
                    return d;
                })
                .collect(java.util.stream.Collectors.toList());

        // === 4) 組裝 ACK ===
        S044AckPayload.Message message = new S044AckPayload.Message();
        message.setSafetyDeviceList(deviceList);

        S044AckPayload ack = new S044AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S044");
        ack.setTid(command.getTid());
        ack.setIdDesc("SAFETY_DEVICE_LIST_CHECK");
        ack.setResult("OK");
        // 如果清單為空，給個簡單訊息 "EMPTY"，否則空字串
        ack.setResultMessage(deviceList.isEmpty() ? "EMPTY" : "");
        ack.setMessage(message);

        // === 5) 發送 ACK ===
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
        log.info("[S044] 已回覆安全 Sensor 清單，count={}", deviceList.size());
    }

    /** Router 用：指定本 Handler 對應的 CMD_ID */
    @Override
    protected String getCmdIdInternal() {
        return "S044";
    }

    /** Jackson 用：指定反序列化的型別 */
    @Override
    protected Class<S044CommandPayload> getCommandType() {
        return S044CommandPayload.class;
    }

    // ===================== 小工具（安全處理字串/十六進位） =====================

    /**
     * 將可能為 null 的字串轉為空字串
     */
    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * trim 後若為 null 則回空字串
     */
    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * 移除 group_word 前綴 'W'（若存在），例如 "W1044" -> "1044"
     * 若長度不足或不是以 'W' 開頭，則盡量回傳合理內容：
     * - null/空字串 -> 空字串
     * - "1044"      -> "1044"（不變）
     */
    private static String stripPrefixW(String groupWord) {
        String t = trimOrEmpty(groupWord);
        if (t.isEmpty()) return t;
        // 僅當第一個字為 'W' 或 'w' 時去掉
        if (t.charAt(0) == 'W' || t.charAt(0) == 'w') {
            return t.substring(1);
        }
        return t;
    }

    /**
     * 以十六進位解析字串（大小寫不敏感），失敗則回 0。
     * 用於 group_word(去掉W後) 與 bit_hex 的排序。
     * 例： "1044" -> 0x1044； "A" -> 0xA； "" -> 0。
     */
    private static int parseHexSafe(String hex) {
        String t = trimOrEmpty(hex).toUpperCase(Locale.ROOT);
        if (t.isEmpty()) return 0;
        try {
            return Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return 0; // 不合法時不拋例外，維持穩定排序
        }
    }
}
