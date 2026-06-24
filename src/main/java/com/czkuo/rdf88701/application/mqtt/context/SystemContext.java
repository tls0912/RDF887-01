package com.czkuo.rdf88701.application.mqtt.context;

import com.czkuo.rdf88701.config.mqtt.MqttConfigProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * SystemContext
 * - 提供本系統的「代號識別」資訊（如：saa、seec、ase）
 * - 雖然目前設定來源為 MQTT 的配置，但本類別為通用設計，可擴充支援其他配置來源
 * - 可作為 sender/receiver 判斷、訊息發送來源、log 記錄等通用用途
 *
 * 來源對應 application.yml：
 * mqtt:
 *   client:
 *     system: saa
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
@RequiredArgsConstructor
public class SystemContext {

    private final MqttConfigProperties mqttConfigProperties;

    /**
     * 取得本系統的代號（系統識別碼）
     * - 預設皆轉為小寫，以利統一比較與記錄
     *
     * @return 小寫系統代號（例如：saa、seec、ase）
     */
    public String getSystemCode() {
        return mqttConfigProperties.getClient().getSystem().toLowerCase();
    }
}
