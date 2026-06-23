package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.WorkingBeamRequestMonitorService;
import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.config.plc.PlcWorkingBeamRegistry;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler Job → 掃描每一台 Working Beam 的未處理請求
 * 每秒執行 → 拆分 per device 處理（每次僅處理一筆）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkingBeamRequestMonitor {

    @Resource
    private MonitorPoolDispatcher dispatcher;
    private final PlcWorkingBeamRegistry workingBeamRegistry;
    private final WorkingBeamRequestMonitorService monitorService;

    /**
     * 每秒掃描各個 WorkingBeam 的 Request，逐台處理 1 筆（若有）
     */
    @Scheduled(fixedDelay = 50)
    public void triggerMonitorPerDevice() {
        for (Long id : workingBeamRegistry.getAllWorkingBeamIds()) {
            dispatcher.submit("[dispatcher]WorkingBeam#{" + id + "} RequestMonitor", () -> triggerDevice(id));
//            try {
//                boolean success = monitorService.monitorUnacceptedRequestsByDevice(String.valueOf(id));
//                if (success) {
//                    log.info("[Scheduler] WorkingBeam#{} 已處理 1 筆 request", id);
//                } else {
//                    //log.debug("[Scheduler] WorkingBeam#{} 無待處理 request", id);
//                }
//            } catch (Exception ex) {
//                log.error("[Scheduler] WorkingBeam#{} monitor 發生例外: {}", id, ex.getMessage(), ex);
//            }
        }
    }

    public void triggerDevice(Long id) {
        try {
            boolean success = monitorService.monitorUnacceptedRequestsByDevice(String.valueOf(id));
            if (success) {
                log.info("[Scheduler] WorkingBeam#{} 已處理 1 筆 request", id);
            } else {
                //log.debug("[Scheduler] WorkingBeam#{} 無待處理 request", id);
            }
        } catch (Exception ex) {
            log.error("[Scheduler] WorkingBeam#{} monitor 發生例外: {}", id, ex.getMessage(), ex);
        }
    }
}
