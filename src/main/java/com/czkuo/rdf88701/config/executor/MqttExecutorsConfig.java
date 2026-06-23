package com.czkuo.rdf88701.config.executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class MqttExecutorsConfig {

    @Bean("mqttInboxExecutor")
    public Executor mqttInboxExecutor() {
        ThreadPoolTaskExecutor tp = new ThreadPoolTaskExecutor();
        tp.setCorePoolSize(4);                      // 視吞吐調
        tp.setMaxPoolSize(16);
        tp.setQueueCapacity(2000);                  // 有界佇列，避免 OOM
        tp.setThreadNamePrefix("mqtt-inbox-");
        tp.setAllowCoreThreadTimeOut(true);
        // QoS0 建議丟最舊的，確保最新消息更可能被處理
        tp.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        tp.setWaitForTasksToCompleteOnShutdown(false);
        tp.initialize();
        return tp;
    }

    @Bean("mqttOutboxExecutor")
    public Executor mqttOutboxExecutor() {
        ThreadPoolTaskExecutor tp = new ThreadPoolTaskExecutor();
        tp.setCorePoolSize(2);                               // 可用屬性外部化
        tp.setMaxPoolSize(4);
        tp.setQueueCapacity(1000);
        tp.setThreadNamePrefix("outbox-");
        tp.setAllowCoreThreadTimeOut(true);
        tp.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // 背壓
        tp.setWaitForTasksToCompleteOnShutdown(true);        // 優雅關閉
        tp.setAwaitTerminationSeconds(5);
        tp.initialize();
        return tp;
    }

    @Bean("mqttAuditExecutor")
    public Executor mqttAuditExecutor() {
        ThreadPoolTaskExecutor tp = new ThreadPoolTaskExecutor();
        tp.setCorePoolSize(1);
        tp.setMaxPoolSize(2);
        tp.setQueueCapacity(200);
        tp.setThreadNamePrefix("mqtt-audit-");
        tp.setAllowCoreThreadTimeOut(true);
        tp.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        tp.setWaitForTasksToCompleteOnShutdown(true);
        tp.setAwaitTerminationSeconds(5);
        tp.initialize();
        return tp;
    }
}
