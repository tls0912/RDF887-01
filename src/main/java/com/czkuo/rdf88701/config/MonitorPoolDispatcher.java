package com.czkuo.rdf88701.config;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;


@Component
public class MonitorPoolDispatcher {

    @Resource(name = "monitorPool")
    private ExecutorService monitorPool;
    private final ConcurrentHashMap<String, AtomicBoolean> flags = new ConcurrentHashMap<>();

    public void submit(String key, Runnable task) {

        AtomicBoolean flag = flags.computeIfAbsent(key, k -> new AtomicBoolean(false));

        if (!flag.compareAndSet(false, true)) {
            return; // 防重入
        }

        try {
            monitorPool.execute(() -> {
                try {
                    task.run();
                } finally {
                    flag.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            flag.set(false);
        }
    }
}
