package com.czkuo.rdf88701.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Configuration
public class SecondaryPortConfig {

    @Bean
    public ServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();

        // 預設 port 走 application.yml 的 server.port
        // 這裡加第二個連接器（6001）
        tomcat.addAdditionalTomcatConnectors(createSecondaryConnector());

        return tomcat;
    }

    private org.apache.catalina.connector.Connector createSecondaryConnector() {
        org.apache.catalina.connector.Connector connector =
                new org.apache.catalina.connector.Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(6001);
        return connector;
    }
}
