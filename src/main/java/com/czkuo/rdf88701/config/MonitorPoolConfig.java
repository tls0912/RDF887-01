package com.czkuo.rdf88701.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

