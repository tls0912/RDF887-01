package com.czkuo.rdf88701.infra.emqx;

import com.czkuo.rdf88701.config.mqtt.EmqxRestProperties;
import com.czkuo.rdf88701.config.mqtt.MqttConfigProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 把「期望訂閱」整理成 system -> List<TopicQos>
 * 預設使用 mqtt.connections[*].recvTopic（QoS=0）
 * 若 emqx.expected-subscriptions 有設定，則以覆寫為準（可多 topic）
 */
@Component
@RequiredArgsConstructor
public class ExpectedSubscriptionsRegistry {

    private final MqttConfigProperties mqttProps;
    private final EmqxRestProperties emqxProps;

    /** 回傳所有 system 的期望訂閱（system key 你已在 MqttConfigProperties 正規化為小寫） */
    public Map<String, List<EmqxRestProperties.TopicQos>> build() {
        Map<String, List<EmqxRestProperties.TopicQos>> result = new LinkedHashMap<>();

        // 1) 先用 connections 的 recvTopic 建預設（QoS=0）
        mqttProps.getConnections().forEach((system, cfg) -> {
            List<EmqxRestProperties.TopicQos> list = new ArrayList<>();
            String recv = cfg.getRecvTopic();
            if (recv != null && !recv.isBlank()) {
                EmqxRestProperties.TopicQos t = new EmqxRestProperties.TopicQos();
                t.setTopic(recv);
                t.setQos(0);
                list.add(t);
            }
            result.put(system, list);
        });

        // 2) 若 emqx.expected-subscriptions 有指定，該 system 以覆寫為準
        Map<String, List<EmqxRestProperties.TopicQos>> overrides = emqxProps.getExpectedSubscriptions();
        if (overrides != null && !overrides.isEmpty()) {
            overrides.forEach((system, list) -> {
                // 正規化 system key（你在 MqttConfigProperties 已做小寫；這裡保險再小寫一次）
                String key = (system == null) ? "" : system.trim().toLowerCase(Locale.ROOT);
                result.put(key, (list == null) ? new ArrayList<>() : new ArrayList<>(list));
            });
        }

        return result;
    }

    /** 取單一 system 的期望訂閱（若無則回空集合，不回 null） */
    public List<EmqxRestProperties.TopicQos> forSystem(String system) {
        if (system == null) return List.of();
        String key = system.trim().toLowerCase(Locale.ROOT);
        return build().getOrDefault(key, List.of());
    }
}
