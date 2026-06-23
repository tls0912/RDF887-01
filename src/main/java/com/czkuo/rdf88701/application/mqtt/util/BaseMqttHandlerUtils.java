package com.czkuo.rdf88701.application.mqtt.util;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * BaseMqttHandlerUtils
 * - 提供共用的 JSON 欄位抽取與格式驗證工具方法（供 Handler 使用）
 * - 避免各個 Handler 重複撰寫欄位解析邏輯
 * - 特別處理 TID 欄位（需符合 yyyyMMddHHmmssSSS 格式）
 */
@UtilityClass
public class BaseMqttHandlerUtils {

    private static final Logger log = LoggerFactory.getLogger(BaseMqttHandlerUtils.class);

    // TID 的預期時間格式：yyyyMMddHHmmssSSS（毫秒精度）
    private static final DateTimeFormatter TID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    // 上一次產生的 TID（用於避免重複）
    private static String lastTid = "";
    private static final Object lock = new Object();

    /**
     * 從 payload 中解析 CMD_ID 欄位（不區分大小寫）
     *
     * @param objectMapper Jackson 物件
     * @param payload      原始 JSON 字串
     * @return CMD_ID 字串，若找不到則回傳 null
     */
    public String extractCmdId(ObjectMapper objectMapper, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            return root.path("CMD_ID").asText(null); // 傳回 null 而不是空字串
        } catch (Exception e) {
            log.warn("❗ extractCmdId() JSON 解析失敗：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 從 payload 中解析並驗證 TID 欄位
     * - 若格式非法，會 log warning 並回傳 null
     *
     * @param objectMapper Jackson 物件
     * @param payload      原始 JSON 字串
     * @return 合法格式的 TID，否則為 null
     */
    public String extractAndValidateTid(ObjectMapper objectMapper, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String tid = root.path("TID").asText(null);
            if (!isValidTidFormat(tid)) {
                log.warn("⚠️ TID 格式不合法：{}", tid);
                return null;
            }
            return tid;
        } catch (Exception e) {
            log.warn("❗ extractAndValidateTid() JSON 解析失敗：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 驗證 TID 是否為合法格式 yyyyMMddHHmmssSSS（17 碼數字 + 時間有效）
     *
     * @param tid 欲驗證的 TID 字串
     * @return true = 合法時間格式
     */
    public boolean isValidTidFormat(String tid) {
        if (StringUtils.isBlank(tid) || tid.length() != 17 || !StringUtils.isNumeric(tid)) {
            return false;
        }
        try {
            LocalDateTime.parse(tid, TID_FORMATTER); // 驗證格式與實際值
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * 產生符合 yyyyMMddHHmmssSSS 格式的 TID
     * - 僅用於簡單情境（不推薦正式用於 MQTT 指令）
     * - 產生符合 yyyyMMddHHmmssSSS 格式的 TID（毫秒級），**不保證唯一性**
     *
     * @return 格式正確的 TID 字串（17碼）
     */
    public String generateTid() {
        return LocalDateTime.now().format(TID_FORMATTER);
    }

    /**
     * 產生符合格式且「保證唯一」的 TID（建議正式指令使用此方法）
     * - 若同一毫秒被多執行緒呼叫，會強制等到下一毫秒再產出
     *
     * @return 格式正確、內容唯一的 TID 字串（17碼）
     */
    public String generateUniqueTid() {
        synchronized (lock) {
            String newTid;
            do {
                newTid = LocalDateTime.now().format(TID_FORMATTER);
            } while (newTid.equals(lastTid));
            lastTid = newTid;
            return newTid;
        }
    }
}
