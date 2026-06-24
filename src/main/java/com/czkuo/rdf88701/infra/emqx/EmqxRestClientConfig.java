package com.czkuo.rdf88701.infra.emqx;

import com.czkuo.rdf88701.config.mqtt.EmqxRestProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Configuration
@RequiredArgsConstructor
public class EmqxRestClientConfig {

    private final EmqxRestProperties props;

    @Bean("emqxRestClient")
    public RestClient emqxRestClient(RestClient.Builder builder) {
        // 用 Java 內建 HttpClient 設定連線逾時
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        // Spring 的 JDK 工廠；支援讀取逾時（6.2+）
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));

        return builder
                .baseUrl(props.getBaseUrl())
                .defaultHeaders(h -> {
                    h.setBasicAuth(props.getAppId(), props.getAppSecret());
                    h.set("Accept", "application/json");
                })
                .requestFactory(factory)
                .build();
    }
}
