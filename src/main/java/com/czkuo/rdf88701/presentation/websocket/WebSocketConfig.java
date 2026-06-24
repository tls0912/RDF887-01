package com.czkuo.rdf88701.presentation.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket STOMP 基礎設定。
 *
 * <p>提供前端 `/ws` 連線端點，啟用 SockJS，並設定 `/topic` 作為後端主動推播
 * 的 broker prefix。前端若需送訊息給後端，使用 `/app` 作為 application prefix。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 註冊前端連接點
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 定義前端訂閱的 prefix
        registry.enableSimpleBroker("/topic");
        // 定義客戶端發送訊息的 prefix（通常不需要特別設）
        registry.setApplicationDestinationPrefixes("/app");
    }
}
