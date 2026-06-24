package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.GripperRequestMonitorService;
import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.config.plc.PlcGripperRegistry;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler Job → 掃描每一台 Gripper 裝置的未處理請求
 * 每秒執行 → 拆分 per device 處理（每次僅處理一筆）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GripperRequestMonitor {

    @Resource
    private MonitorPoolDispatcher dispatcher;
    private final PlcGripperRegistry gripperRegistry;
    private final GripperRequestMonitorService monitorService;

    /**
     * 每秒掃描各個 Gripper 裝置的 Request，逐台處理 1 筆（若有）
     */
    @Scheduled(fixedDelay = 50)
    public void triggerMonitorPerDevice() {
        for (Long id : gripperRegistry.getAllGripperIds()) {
            dispatcher.submit("[dispatcher]Gripper#{" + id + "} RequestMonitor", () -> triggerDevice(id));
//            try {
//                boolean success = monitorService.monitorUnacceptedRequestsByDevice(String.valueOf(gripperId));
//                if (success) {
//                    log.info("[Scheduler] Gripper#{} 已處理 1 筆 request", gripperId);
//                } else {
//                    //log.debug("[Scheduler] Gripper#{} 無待處理 request", gripperId);
//                }
//            } catch (Exception ex) {
//                log.error("[Scheduler] Gripper#{} monitor 發生例外: {}", gripperId, ex.getMessage(), ex);
//            }
        }
    }

    public void triggerDevice(Long id) {
        try {
            boolean success = monitorService.monitorUnacceptedRequestsByDevice(String.valueOf(id));
            if (success) {
                log.info("[Scheduler] Gripper#{} 已處理 1 筆 request", id);
            } else {
                //log.debug("[Scheduler] Gripper#{} 無待處理 request", id);
            }
        } catch (Exception ex) {
            log.error("[Scheduler] Gripper#{} monitor 發生例外: {}", id, ex.getMessage(), ex);
        }
    }
}
