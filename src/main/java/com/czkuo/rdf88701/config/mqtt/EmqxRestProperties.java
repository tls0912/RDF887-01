package com.czkuo.rdf88701.config.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * EMQX REST 設定 +（可選）每個 system 的宣告式 expected topics
 * 不填 expectedSubscriptions 也行，會 fallback 到 mqtt.connections[*].recvTopic
 */
@Data
@Component
@ConfigurationProperties(prefix = "emqx")
public class EmqxRestProperties {

    /** 例如 http://localhost:18083 （EMQX Dashboard API 基址）*/
    private String baseUrl;

    /** Dashboard/App 的 AppID（Basic Auth）*/
    private String appId;

    /** Dashboard/App 的 AppSecret（Basic Auth）*/
    private String appSecret;

    /**
     * （可選）覆寫每個 system 的期望訂閱清單：
     *   key = system 名稱（對應 mqtt.connections 的 key，小寫）
     *   value = 該 system 期望訂閱的 topic+qos 列表
     */
    private Map<String, List<TopicQos>> expectedSubscriptions;

    @Data
    public static class TopicQos {
        private String topic;
        private int qos = 0;
    }
}
