package com.czkuo.rdf88701.config.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Slf4j
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
        ts.setPoolSize(60);                  // 先拉到 16（你有 40+ 任務），之後再依佇列觀察調整
        ts.setThreadNamePrefix("sched-");
        ts.setRemoveOnCancelPolicy(true);
        ts.setWaitForTasksToCompleteOnShutdown(true);
        ts.setAwaitTerminationSeconds(5);
        ts.setErrorHandler(t -> log.error("[SCHED] uncaught error in scheduled task", t));
        ts.initialize();
        return ts;
    }

    @Bean("mqttScheduler")
    public TaskScheduler mqttScheduler() {
        ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
        ts.setPoolSize(3);                        // 心跳/逾時兩條足夠；要更穩可以 3
        ts.setThreadNamePrefix("mqtt-sched-");
        ts.setRemoveOnCancelPolicy(true);
        ts.initialize();
        return ts;
    }
}
