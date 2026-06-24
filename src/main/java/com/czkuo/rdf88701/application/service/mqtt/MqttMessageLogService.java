package com.czkuo.rdf88701.application.service.mqtt;

import com.czkuo.rdf88701.application.mqtt.util.MqttPayloadSanitizer;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.config.mqtt.MqttConfigProperties;
import com.czkuo.rdf88701.domain.repository.MqttMessageLogRepository;
import com.czkuo.rdf88701.infra.entity.MqttMessageLog;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * MqttMessageLogService
 * - 統一記錄所有 MQTT 指令與回覆（COMMAND / ACK）
 * - ★ 寫入前可選擇清洗敏感欄位（MqttPayloadSanitizer）並限制 payload 最大長度
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttMessageLogService {

    private final MqttMessageLogRepository logRepository;
    private final MqttMessageLogQueueService logQueueService;
    private final ObjectMapper objectMapper;
    private final MqttConfigProperties mqttConfigProperties;

    /** 可選：清洗器（未註冊時為 null，則不清洗） */
    private final MqttPayloadSanitizer payloadSanitizer;

    /** 是否啟用寫表清洗（僅影響 DB，不影響實際送出的內容） */
    @Value("${mqtt.audit.sanitize-enabled:true}")
    private boolean sanitizeEnabled;

    /** 寫表時 payload 允許的最大位元組數（UTF-8），超過則截斷；<=0 表示無上限 */
    @Value("${mqtt.audit.truncate-max-bytes:0}")
    private int truncateMaxBytes;

    /**
     * 取得本系統代號（通常為 SAA），來自 application.yml
     */
    public String getLocalSystem() {
        return mqttConfigProperties.getClient().getSystem();
    }

    // =====================================================================
    // 寫入記錄（會先清洗、再截斷）
    // =====================================================================

    public void record(String topic, String sender, String receiver, JsonNode payload, MqttMessageType type) {
        try {
            MqttMessageLog mqttLog = new MqttMessageLog();

            // 主要欄位
            mqttLog.setTid(payload.path("TID").asText());
            mqttLog.setCmdId(payload.path("CMD_ID").asText());
            mqttLog.setIdDesc(payload.path("ID_DESC").asText());
            mqttLog.setTopic(topic);
            mqttLog.setSender(sender);
            mqttLog.setReceiver(receiver);
            mqttLog.setTimestamp(LocalDateTime.now());
            mqttLog.setMessageType(type.name());

            // ACK 特有欄位
            if (type == MqttMessageType.ACK) {
                mqttLog.setResult(payload.path("RESULT").asText(null));
                mqttLog.setResultMessage(payload.path("RESULT_MESSAGE").asText(null));
            }

            // 清洗 + 截斷 → 再存入
            String safeJson = prepareForStorage(payload);
            mqttLog.setPayload(safeJson);

            // 寫入
            logRepository.save(mqttLog);
            //log.debug("[MQTT][LOG] 已記錄訊息：TID={}, CMD_ID={}, TYPE={}", mqttLog.getTid(), mqttLog.getCmdId(), type);

        } catch (Exception e) {
            log.error("❌ 儲存 MQTT 訊息紀錄失敗：{}", e.getMessage(), e);
        }
    }

    /**
     * Use only for audit logs that do not need an immediate id and are not read by flow decisions.
     */
    public void recordQueued(String topic, String sender, String receiver, JsonNode payload, MqttMessageType type) {
        try {
            MqttMessageLog mqttLog = new MqttMessageLog();

            mqttLog.setTid(payload.path("TID").asText());
            mqttLog.setCmdId(payload.path("CMD_ID").asText());
            mqttLog.setIdDesc(payload.path("ID_DESC").asText());
            mqttLog.setTopic(topic);
            mqttLog.setSender(sender);
            mqttLog.setReceiver(receiver);
            mqttLog.setTimestamp(LocalDateTime.now());
            mqttLog.setMessageType(type.name());

            if (type == MqttMessageType.ACK) {
                mqttLog.setResult(payload.path("RESULT").asText(null));
                mqttLog.setResultMessage(payload.path("RESULT_MESSAGE").asText(null));
            }

            String safeJson = prepareForStorage(payload);
            mqttLog.setPayload(safeJson);

            logQueueService.enqueue(mqttLog, type);
        } catch (Exception e) {
            log.error("Failed to queue MQTT message log: {}", e.getMessage(), e);
        }
    }

    public Long recordReturningId(String topic, String sender, String receiver, JsonNode payload, MqttMessageType type) {
        try {
            MqttMessageLog mqttLog = new MqttMessageLog();

            mqttLog.setTid(payload.path("TID").asText());
            mqttLog.setCmdId(payload.path("CMD_ID").asText());
            mqttLog.setIdDesc(payload.path("ID_DESC").asText());
            mqttLog.setTopic(topic);
            mqttLog.setSender(sender);
            mqttLog.setReceiver(receiver);
            mqttLog.setTimestamp(LocalDateTime.now());
            mqttLog.setMessageType(type.name());

            if (type == MqttMessageType.ACK) {
                mqttLog.setResult(payload.path("RESULT").asText(null));
                mqttLog.setResultMessage(payload.path("RESULT_MESSAGE").asText(null));
            }

            // 清洗 + 截斷
            String safeJson = prepareForStorage(payload);
            mqttLog.setPayload(safeJson);

            boolean ok = logRepository.save(mqttLog);
            Long id = mqttLog.getId();

            if (!ok || id == null) {
                log.error("[MQTT][LOG] 插入失敗或未回填主鍵：TID={}, CMD_ID={}, TYPE={}",
                        mqttLog.getTid(), mqttLog.getCmdId(), type);
                return null;
            }

            //log.debug("[MQTT][LOG] 已記錄訊息(回傳ID)：id={}, TID={}, CMD_ID={}, TYPE={}",
//                    id, mqttLog.getTid(), mqttLog.getCmdId(), type);
            return id;

        } catch (Exception e) {
            log.error("❌ 儲存 MQTT 訊息紀錄（回傳ID）失敗：{}", e.getMessage(), e);
            return null;
        }
    }

    // =====================================================================
    // 查詢輔助（維持原樣）
    // =====================================================================

    public JsonNode getPayloadById(Long id) {
        try {
            if (id == null) return null;
            Optional<MqttMessageLog> opt = logRepository.findById(id);
            if (opt.isEmpty()) return null;

            String payload = opt.get().getPayload();
            if (payload == null || payload.isBlank()) return null;

            try {
                return objectMapper.readTree(payload);
            } catch (Exception parseEx) {
                log.warn("[MQTT][LOG] 解析 payload 失敗，id={}：{}", id, parseEx.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.warn("[MQTT][LOG] 讀取 payload 失敗，id={}：{}", id, e.getMessage());
            return null;
        }
    }

    public String getPayloadStringById(Long id) {
        try {
            if (id == null) return null;
            return logRepository.findById(id).map(MqttMessageLog::getPayload).orElse(null);
        } catch (Exception e) {
            log.warn("[MQTT][LOG] 讀取 payload 字串失敗，id={}：{}", id, e.getMessage());
            return null;
        }
    }

    public boolean hasAckStart(String tid, String cmdId) {
        try {
            return logRepository.existsAckStart(tid, cmdId);
        } catch (Exception e) {
            log.warn("[MQTT][LOG] 查詢 ACK=START 失敗（將視為未送過）：tid={}, cmdId={}, err={}",
                    tid, cmdId, e.getMessage());
            return false;
        }
    }

    // =====================================================================
    // 內部：清洗 + 截斷
    // =====================================================================

    /**
     * 只在「寫 DB」前做處理：
     * 1) 可選擇以 MqttPayloadSanitizer 清洗
     * 2) 依 truncateMaxBytes 進行位元組數限制（UTF-8）
     */
    private String prepareForStorage(JsonNode original) {
        JsonNode node = original;

        // 1) 清洗（若有注入且開啟）
        if (sanitizeEnabled && payloadSanitizer != null) {
            try {
                node = payloadSanitizer.sanitizeForLog(node);
            } catch (Exception e) {
                log.warn("[MQTT][LOG] sanitize 失敗（將改存原文）：{}", e.toString());
                node = original;
            }
        }

        // 2) 轉字串
        String jsonText;
        try {
            jsonText = objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("❗ payload 轉字串失敗（將略過儲存原始 JSON）：{}", e.getMessage());
            return null; // 允許存 null，避免影響主流程
        }

        // 3) 截斷（若有上限）
        if (truncateMaxBytes > 0) {
            byte[] bytes = jsonText.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > truncateMaxBytes) {
                byte[] cut = cutUtf8(bytes, truncateMaxBytes);
                String truncated = new String(cut, StandardCharsets.UTF_8);
                log.warn("[MQTT][LOG] payload 超過上限，已截斷：len={} > limit={}", bytes.length, truncateMaxBytes);
                return truncated;
            }
        }
        return jsonText;
    }

    /**
     * 以 UTF-8 位元組上限安全截斷（避免切斷多位元組造成亂碼）
     */
    private static byte[] cutUtf8(byte[] src, int maxBytes) {
        if (src.length <= maxBytes) return src;
        // 嘗試在 maxBytes 內回退到合法 UTF-8 邊界
        int end = maxBytes;
        while (end > 0) {
            byte b = src[end - 1];
            // UTF-8 前綴 byte：10xxxxxx (續字節) → 繼續回退
            if ((b & 0b1100_0000) == 0b1000_0000) {
                end--;
                continue;
            }
            break;
        }
        if (end <= 0) {
            // 萬一全部都落在續字節，保守回傳空陣列
            return new byte[0];
        }
        ByteArrayBuilder bab = new ByteArrayBuilder(end);
        bab.write(src, 0, end);
        return bab.toByteArray();
    }
}
