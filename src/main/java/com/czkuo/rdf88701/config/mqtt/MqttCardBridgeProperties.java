package com.czkuo.rdf88701.config.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 設定：CardReadEvent → MQTT S010 橋接
 *
 * YAML 範例：
 * mqtt:
 *   card:
 *     enabled: true
 *     targetSystem: seec
 *     debounceMs: 300
 *     aliasTarget:
 *       # card6: ase
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mqtt.card")
public class MqttCardBridgeProperties {

    /** 總開關：false 不發 S010 */
    private boolean enabled = true;

    /** 預設目標系統（例：seec / ase），可被 aliasTarget 覆蓋 */
    private String targetSystem = "seec";

    /** 去抖時間（毫秒）；0 表示不去抖 */
    private long debounceMs = 300;

    /** 依 alias 覆蓋目標系統，例如：card6 -> ase */
    private Map<String, String> aliasTarget = new HashMap<>();
}