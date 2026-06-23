package com.czkuo.rdf88701.application.mqtt.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * MqttPayloadSanitizer
 * ------------------------------------------------------------
 * 用於「寫入資料庫或審計」前，清洗 MQTT 的 JSON payload：
 * - 移除/遮罩巨量二進位欄位（S072/S073 影像等）
 * - 遮罩敏感欄位（如 cardNumber）
 * - 截斷過長字串
 * - 裁切過大的陣列，附上註記
 *
 * 預設即能工作；如需調整規則，請使用提供的 setter。
 *
 * 用法：
 *   MqttPayloadSanitizer s = new MqttPayloadSanitizer();
 *   JsonNode safe = s.sanitizeForLog(originalJsonNode);
 *   // 然後把 safe 寫入 DB
 */
public class MqttPayloadSanitizer {

    private static final Logger log = LoggerFactory.getLogger(MqttPayloadSanitizer.class);

    // —— 常數標記 ——
    private static final String OMIT_ARRAY_NOTE = "[[OMITTED:ARRAY-TRUNCATED]]";
    private static final String OMIT_LONG_NOTE  = "… (omitted)";

    // —— 偵測正則 ——
    private static final Pattern DATA_URL_PREFIX = Pattern.compile("^data:[^;]+;base64,", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIKELY_B64      = Pattern.compile("^[A-Za-z0-9+/\\r\\n]+=*$");

    // —— 預設設定值（可被 setter 覆寫） ——
    /** 長字串最大長度，超過即截斷並附註 */
    private int maxStringLength = 2000;

    /** 是否將 Data URL 一律視為二進位 */
    private boolean treatDataUrlAsBinary = true;

    /** 針對疑似 base64 長字串的判斷最小長度；小於此長度不嘗試 base64 判斷 */
    private int minBase64DetectLength = 64;

    /** 陣列最大保留項目數；超過則裁切並加上註記 */
    private int maxArrayItems = 200;

    /** 將二進位欄位替換為 {"_omitted":"BINARY"}；若 >0，會加上 "_preview" 前 n 字元 */
    private int binaryPreviewLen = 0;

    /** 欄位名稱（正則）符合者視為二進位欄位（大小寫不敏感） */
    private List<Pattern> binaryFieldPatterns = new ArrayList<>(List.of(
            Pattern.compile(".*(image|img|photo|picture|jpeg|jpg|png|bmp|gif)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("UpperCoverTray.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("TrayFront.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("TrayBack.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("TrayLeftImage", Pattern.CASE_INSENSITIVE),
            Pattern.compile("TrayRightImage", Pattern.CASE_INSENSITIVE)
    ));

    /** 需遮罩的敏感欄位名稱（完全比對，不分大小寫） */
    private Set<String> sensitiveFieldNames = new HashSet<>(List.of(
            "cardNumber", "password", "secret", "token", "apikey"
    ));

    private final ObjectMapper objectMapper;

    // —— 建構 —— //
    public MqttPayloadSanitizer() {
        this(new ObjectMapper());
    }

    public MqttPayloadSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    }

    // —— 對外 API —— //
    /** 傳回「清洗後副本」；不會修改傳入的 JsonNode。 */
    public JsonNode sanitizeForLog(JsonNode original) {
        if (original == null || original.isNull()) return NullNode.instance;
        try {
            JsonNode copy = original.deepCopy();
            return cleanNode(null, copy);
        } catch (Exception e) {
            log.warn("[Sanitizer] sanitizeForLog error: {}", e.toString());
            return TextNode.valueOf("[[SANITIZE_ERROR]]");
        }
    }

    // —— 遞迴清洗 —— //
    private JsonNode cleanNode(String key, JsonNode node) {
        if (node == null || node.isNull()) return NullNode.instance;

        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                var e = it.next();
                obj.set(e.getKey(), cleanNode(e.getKey(), e.getValue()));
            }
            return obj;
        }

        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            int limit = Math.max(1, maxArrayItems);
            if (arr.size() > limit) {
                ArrayNode trimmed = objectMapper.createArrayNode();
                for (int i = 0; i < limit; i++) {
                    trimmed.add(cleanNode(key, arr.get(i)));
                }
                ObjectNode note = objectMapper.createObjectNode();
                note.put("_note", OMIT_ARRAY_NOTE);
                note.put("_original_size", arr.size());
                trimmed.add(note);
                return trimmed;
            }
            ArrayNode cleaned = objectMapper.createArrayNode();
            for (int i = 0; i < arr.size(); i++) {
                cleaned.add(cleanNode(key, arr.get(i)));
            }
            return cleaned;
        }

        if (node.isTextual()) {
            String val = node.asText();

            // 1) 二進位欄位（依欄位名或值型態判斷）
            if (isBinaryField(key) || isDataUrlOrBase64(val)) {
                return omittedBinaryNode(val);
            }

            // 2) 敏感欄位遮罩
            if (isSensitiveField(key)) {
                return TextNode.valueOf(maskSensitive(val));
            }

            // 3) 過長字串截斷
            if (val != null && val.length() > maxStringLength) {
                return TextNode.valueOf(val.substring(0, maxStringLength) + OMIT_LONG_NOTE);
            }
            return node;
        }

        if (node.isBinary()) {
            return omittedBinaryNode("[binary]");
        }

        // 數字/布林照原樣
        return node;
    }

    private boolean isBinaryField(String key) {
        if (key == null) return false;
        for (Pattern p : binaryFieldPatterns) {
            if (p.matcher(key).find()) return true;
        }
        return false;
    }

    private boolean isSensitiveField(String key) {
        if (key == null) return false;
        for (String n : sensitiveFieldNames) {
            if (n.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    private boolean isDataUrlOrBase64(String s) {
        if (s == null || s.isBlank()) return false;
        if (treatDataUrlAsBinary && DATA_URL_PREFIX.matcher(s).find()) return true;

        if (s.length() >= Math.max(16, minBase64DetectLength) &&
                LIKELY_B64.matcher(s.replace("\n", "").replace("\r", "")).matches()) {
            try {
                // 不是所有 base64 都是影像，但此處只為了「避免把巨大內容寫進 DB」
                Base64.getDecoder().decode(s.getBytes(StandardCharsets.US_ASCII));
                return true;
            } catch (Exception ignore) {
                return false;
            }
        }
        return false;
    }

    private JsonNode omittedBinaryNode(String original) {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("_omitted", "BINARY");
        if (binaryPreviewLen > 0 && original != null) {
            int n = Math.min(original.length(), binaryPreviewLen);
            o.put("_preview", original.substring(0, n));
        }
        return o;
    }

    private String maskSensitive(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() <= 4) return "****";
        String tail = s.substring(s.length() - 4);
        return "**** **** **** " + tail;
    }

    // —— 可選：客製化參數 setter —— //
    public MqttPayloadSanitizer setMaxStringLength(int maxStringLength) {
        this.maxStringLength = Math.max(200, maxStringLength);
        return this;
    }

    public MqttPayloadSanitizer setTreatDataUrlAsBinary(boolean treat) {
        this.treatDataUrlAsBinary = treat;
        return this;
    }

    public MqttPayloadSanitizer setMinBase64DetectLength(int len) {
        this.minBase64DetectLength = Math.max(16, len);
        return this;
    }

    public MqttPayloadSanitizer setMaxArrayItems(int maxArrayItems) {
        this.maxArrayItems = Math.max(1, maxArrayItems);
        return this;
    }

    public MqttPayloadSanitizer setBinaryPreviewLen(int binaryPreviewLen) {
        this.binaryPreviewLen = Math.max(0, binaryPreviewLen);
        return this;
    }

    public MqttPayloadSanitizer clearBinaryFieldPatterns() {
        this.binaryFieldPatterns.clear();
        return this;
    }

    public MqttPayloadSanitizer addBinaryFieldPattern(String regex) {
        if (regex != null && !regex.isBlank()) {
            this.binaryFieldPatterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
        }
        return this;
    }

    public MqttPayloadSanitizer setBinaryFieldPatterns(Collection<String> regexes) {
        this.binaryFieldPatterns.clear();
        if (regexes != null) {
            for (String r : regexes) addBinaryFieldPattern(r);
        }
        return this;
    }

    public MqttPayloadSanitizer setSensitiveFieldNames(Collection<String> names) {
        this.sensitiveFieldNames.clear();
        if (names != null) {
            this.sensitiveFieldNames.addAll(names);
        }
        return this;
    }
}
