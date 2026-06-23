package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.CraneRequestMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler Job → 呼叫 Monitor Service
 * 必須拆分，讓 Service 層可以被 Spring AOP 正確代理 → 解決 synchronization 問題
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CraneRequestMonitor {

    private final CraneRequestMonitorService monitorService;

    /**
     * 每 1 秒掃描一次未處理 request
     * scheduler 不處理資料庫 → 只負責呼叫 service
     */
    @Scheduled(fixedDelay = 150)
    public void triggerMonitor() {
        try {
            monitorService.monitorUnacceptedRequests();
        } catch (Exception ex) {
            log.error("[Scheduler] CraneRequestMonitor 發生例外: {}", ex.getMessage(), ex);
        }
    }
}
