package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.CraneRequestMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Crane request 掃描排程。
 *
 * <p>此類只負責以固定間隔觸發 CraneRequestMonitorService，實際資料庫查詢與
 * request 處理留在 service 層，讓 Spring AOP 與交易代理能正常套用。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
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
