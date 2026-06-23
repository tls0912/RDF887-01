package com.czkuo.rdf88701.config.mqtt;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MqttConnectionProperties
 * 對應 mqtt.connections.[targetSystem] 的每一組對外連線設定。
 */
@Data
public class MqttConnectionProperties {

    /** MQTT Broker 連線位址，例如 tcp://localhost:1883 */
    @NotBlank
    private String broker;

    /** MQTT Client ID（同一 broker 需唯一） */
    @NotBlank
    private String clientId;

    /** 發送用 topic（我方 → 對方） */
    @NotBlank
    private String sendTopic;

    /** 接收用 topic（對方 → 我方） */
    @NotBlank
    private String recvTopic;

    /** 帳號（若 broker 啟用驗證） */
    private String username;

    /** 密碼（若 broker 啟用驗證） */
    private String password;

    // 可按需擴充：
    // private boolean cleanSession = true;
    // private int connectionTimeout = 30;
    // private int keepAliveSeconds = 60;
    // private Integer qos; // 預設 null 交由 publisher 控制
}
