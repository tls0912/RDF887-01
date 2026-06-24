package com.czkuo.rdf88701.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Configuration
public class MonitorPoolConfig {

    @Bean("monitorPool")
    public ExecutorService monitorPool() {
        return new ThreadPoolExecutor(
                110,
                110,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(360),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("monitor-" + t.getId());
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}

