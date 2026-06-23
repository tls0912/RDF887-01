package com.czkuo.rdf88701.config.mqtt;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * MqttConfigProperties
 * - 封裝 application.yml 中以 "mqtt" 開頭的所有 MQTT 相關設定。
 * - 支援自身系統代號（client.system）與多個外部連線（connections）設定。
 *
 * yml 範例：
 *
 * mqtt:
 *   client:
 *     system: saa
 *   connections:
 *     seec:
 *       broker: tcp://localhost:1883
 *       clientId: saa-seec
 *       sendTopic: saa_to_seec
 *       recvTopic: seec_to_saa
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "mqtt")
public class MqttConfigProperties {

    /** 自身系統（用來當作 S001.ID_DESC 預設值等） */
    @Valid
    @NotNull
    private ClientProperties client = new ClientProperties();

    /**
     * 多個對外系統的 MQTT 連線資訊
     * key 建議使用小寫（ex: seec, ase）
     */
    @Valid
    @NotEmpty
    private Map<@NotBlank String, @Valid MqttConnectionProperties> connections = new LinkedHashMap<>();

    @Data
    public static class ClientProperties {
        /** 本系統代號（預設 PC_LINK；建議小寫） */
        @NotBlank
        private String system = "PC_LINK";
    }

    /** 將 connections 的 key 正規化為小寫，避免大小寫帶來的查詢歧異 */
    @PostConstruct
    void normalizeKeysToLowerCase() {
        if (connections == null || connections.isEmpty()) return;
        Map<String, MqttConnectionProperties> norm = new LinkedHashMap<>();
        for (Map.Entry<String, MqttConnectionProperties> e : connections.entrySet()) {
            String k = e.getKey() == null ? "" : e.getKey().trim().toLowerCase(Locale.ROOT);
            if (k.isEmpty()) continue;
            if (norm.containsKey(k)) {
                throw new IllegalStateException("Duplicated mqtt.connections key after lower-casing: " + k);
            }
            norm.put(k, e.getValue());
        }
        this.connections = norm;
    }

    /* ========== 便捷 Helper ========== */

    /** 預設 ID_DESC（S001 用） */
    public String idDesc() {
        String v = (client == null) ? null : client.getSystem();
        return (v != null && !v.isBlank()) ? v : "PC_LINK";
    }

    /** 取對應連線（若不存在丟清楚的錯誤） */
    public MqttConnectionProperties connOrThrow(String keyLowerCase) {
        if (keyLowerCase == null) {
            throw new IllegalArgumentException("connection key is null");
        }
        String k = keyLowerCase.trim().toLowerCase(Locale.ROOT);
        MqttConnectionProperties c = connections.get(k);
        if (c == null) {
            throw new IllegalStateException("Missing mqtt.connections." + k + " config");
        }
        return c;
    }

    /** 取 send topic（我方→對方） */
    public String sendTopic(String keyLowerCase) {
        String t = connOrThrow(keyLowerCase).getSendTopic();
        if (t == null || t.isBlank()) {
            throw new IllegalStateException("Missing sendTopic for mqtt.connections." + keyLowerCase);
        }
        return t;
    }

    /** 取 recv topic（對方→我方） */
    public String recvTopic(String keyLowerCase) {
        String t = connOrThrow(keyLowerCase).getRecvTopic();
        if (t == null || t.isBlank()) {
            throw new IllegalStateException("Missing recvTopic for mqtt.connections." + keyLowerCase);
        }
        return t;
    }
}
