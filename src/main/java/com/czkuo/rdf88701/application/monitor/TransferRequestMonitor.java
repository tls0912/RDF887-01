package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.TransferRequestMonitorService;
import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.config.plc.PlcTransferRegistry;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler Job → 掃描每一台 Transfer 裝置的未處理請求
 * 每秒執行 → 拆分 per device 處理（每次僅處理一筆）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferRequestMonitor {

    @Resource
    private MonitorPoolDispatcher dispatcher;
    private final PlcTransferRegistry transferRegistry;
    private final TransferRequestMonitorService monitorService;

    /**
     * 每秒掃描各個 Transfer 裝置的 Request，逐台處理 1 筆（若有）
     */
    @Scheduled(fixedDelay = 100)
    public void triggerMonitorPerDevice() {
        for (Long id : transferRegistry.getAllTransferIds()) {
            dispatcher.submit("[dispatcher]Transfer#{" + id + "} RequestMonitor", () -> triggerDevice(id));
//            try {
//                boolean success = monitorService.monitorUnacceptedRequestsByDevice(String.valueOf(transferId));
//                if (success) {
//                    log.info("[Scheduler] Transfer#{} 已處理 1 筆 request", transferId);
//                } else {
//                    //log.debug("[Scheduler] Transfer#{} 無待處理 request", transferId);
//                }
//            } catch (Exception ex) {
//                log.error("[Scheduler] Transfer#{} monitor 發生例外: {}", transferId, ex.getMessage(), ex);
//            }
        }
    }

    public void triggerDevice(Long id) {
        try {
            boolean success = monitorService.monitorUnacceptedRequestsByDevice(String.valueOf(id));
            if (success) {
                log.info("[Scheduler] Transfer#{} 已處理 1 筆 request", id);
            } else {
                //log.debug("[Scheduler] Transfer#{} 無待處理 request", id);
            }
        } catch (Exception ex) {
            log.error("[Scheduler] Transfer#{} monitor 發生例外: {}", id, ex.getMessage(), ex);
        }
    }
}
