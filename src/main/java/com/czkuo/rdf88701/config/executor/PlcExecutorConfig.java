package com.czkuo.rdf88701.config.executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 提供專用於 PLC 初始化的執行緒池。
 * 適用於併發啟動裝置連線等非阻塞初始化任務。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Configuration
public class PlcExecutorConfig {

    /**
     * PLC 初始化任務專用執行緒池。
     * <p>
     * 核心用途：
     * - 併發執行 PLC 啟動邏輯（如 connect / health check）
     * - 任務量大時避免主線程阻塞
     * <p>
     * 可調參數說明：
     * - corePoolSize: 初始保留的核心執行緒數（推薦 ≦ CPU 核心數）
     * - maxPoolSize: 最大允許的執行緒數
     * - queueCapacity: 等待佇列長度，超過後會啟動 maxPoolSize
     */
    @Bean(name = "plcInitExecutor")
    public Executor plcInitExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);               // 預設核心執行緒數
        executor.setMaxPoolSize(16);               // 最多允許的執行緒數
        executor.setQueueCapacity(50);             // 佇列長度，超過則啟用 maxPoolSize
        executor.setThreadNamePrefix("PLC-INIT-"); // 自定執行緒名稱前綴，方便日誌追蹤

        // 拒絕策略：任務滿載時直接在呼叫端執行（避免丟失任務）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }
}
