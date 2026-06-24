package com.czkuo.rdf88701.application.service.polling;

import com.czkuo.rdf88701.application.service.polling.handler.*;
import com.czkuo.rdf88701.application.service.polling.scheduler.PollingTaskScheduler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * PlcPollingService (Modularized)
 * - 作為統一入口，負責整合排程啟動、資料監控與推播、資源初始化與關閉流程
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlcPollingService {

    private final PollingTaskScheduler pollingTaskScheduler;
    private final CranePollingHandler cranePollingHandler;
    private final GripperPollingHandler gripperPollingHandler;
    private final WorkingBeamPollingHandler workingBeamPollingHandler;
    private final TransferPollingHandler transferPollingHandler;
    private final StrappingPollingHandler strappingPollingHandler;
    private final SitePollingHandler sitePollingHandler;
    private final InfraredPollingHandler infraredPollingHandler;

    /**
     * 系統啟動後初始化各模組流程。
     */
    @PostConstruct
    public void initialize() {
        log.info("[INIT] PlcPollingService 初始化中...");

        // 初始化狀態機
        cranePollingHandler.initCraneStateMachines();
        gripperPollingHandler.initGripperStateMachines();
        workingBeamPollingHandler.initWorkingBeamStateMachines();
        transferPollingHandler.initTransferStateMachines();
        strappingPollingHandler.initStrappingStateMachines();
        infraredPollingHandler.initInfraredStateMachines();

        // 啟動輪詢任務
        pollingTaskScheduler.startAllPollingTasks();

        // 啟動監控任務
        cranePollingHandler.startDeviceMonitoring();
        cranePollingHandler.startCommandMonitoring();
        gripperPollingHandler.startDeviceMonitoring();
        gripperPollingHandler.startCommandMonitoring();
        workingBeamPollingHandler.startDeviceMonitoring();
        workingBeamPollingHandler.startCommandMonitoring();
        transferPollingHandler.startDeviceMonitoring();
        transferPollingHandler.startCommandMonitoring();
        sitePollingHandler.startDeviceMonitoring();
        sitePollingHandler.startCommandMonitoring();
        strappingPollingHandler.startDeviceMonitoring();
        strappingPollingHandler.startCommandMonitoring();
        infraredPollingHandler.startDeviceMonitoring();
        infraredPollingHandler.startCommandMonitoring();


        // 啟動推播任務
        cranePollingHandler.startDevicePushTask();
        cranePollingHandler.startCommandPushTask();
        gripperPollingHandler.startDevicePushTask();
        gripperPollingHandler.startCommandPushTask();
        workingBeamPollingHandler.startDevicePushTask();
        workingBeamPollingHandler.startCommandPushTask();
        transferPollingHandler.startDevicePushTask();
        transferPollingHandler.startCommandPushTask();
        sitePollingHandler.startDevicePushTask();
        sitePollingHandler.startCommandPushTask();
        strappingPollingHandler.startDevicePushTask();
        strappingPollingHandler.startCommandPushTask();
        infraredPollingHandler.startDevicePushTask();
        infraredPollingHandler.startCommandPushTask();

        log.info("[INIT] PlcPollingService 初始化完成");
    }

    /**
     * 系統關閉前的資源釋放流程。
     */
    @PreDestroy
    public void shutdown() {
        log.info("[SHUTDOWN] PlcPollingService 關閉中...");

        // 停止排程
        pollingTaskScheduler.stopAllPollingTasks();

        // 停止監控
        cranePollingHandler.stopDeviceMonitoring();
        cranePollingHandler.stopCommandMonitoring();
        gripperPollingHandler.stopDeviceMonitoring();
        gripperPollingHandler.stopCommandMonitoring();
        workingBeamPollingHandler.stopDeviceMonitoring();
        workingBeamPollingHandler.stopCommandMonitoring();
        transferPollingHandler.stopDeviceMonitoring();
        transferPollingHandler.stopCommandMonitoring();
        sitePollingHandler.stopDeviceMonitoring();
        sitePollingHandler.stopCommandMonitoring();
        strappingPollingHandler.stopDeviceMonitoring();
        strappingPollingHandler.stopCommandMonitoring();
        infraredPollingHandler.stopDeviceMonitoring();
        infraredPollingHandler.stopCommandMonitoring();

        // 停止推播
        cranePollingHandler.stopDevicePushTask();
        cranePollingHandler.stopCommandPushTask();
        gripperPollingHandler.stopDevicePushTask();
        gripperPollingHandler.stopCommandPushTask();
        workingBeamPollingHandler.stopDevicePushTask();
        workingBeamPollingHandler.stopCommandPushTask();
        transferPollingHandler.stopDevicePushTask();
        transferPollingHandler.stopCommandPushTask();
        sitePollingHandler.stopDevicePushTask();
        sitePollingHandler.stopCommandPushTask();
        strappingPollingHandler.stopDevicePushTask();
        strappingPollingHandler.stopCommandPushTask();
        infraredPollingHandler.stopDevicePushTask();
        infraredPollingHandler.stopCommandPushTask();

        log.info("[SHUTDOWN] PlcPollingService 已安全關閉");
    }
}
