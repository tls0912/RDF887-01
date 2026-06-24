package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.command.CraneHandshakeStateMachine;
import com.czkuo.rdf88701.application.service.query.CraneTaskQueryService;
import com.czkuo.rdf88701.config.plc.PlcCraneRegistry;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneCommandStatus;
import com.czkuo.rdf88701.domain.repository.CraneTaskFollowUpRecordRepository;
import com.czkuo.rdf88701.infra.cache.CraneCommandCache;
import com.czkuo.rdf88701.infra.cache.CraneStatusCache;
import com.czkuo.rdf88701.infra.entity.CraneTaskFollowUpRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Crane 任務監控排程器
 * 每秒監控所有天車
 * 將任務與 PLC 狀態交由狀態機決定如何處理
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CraneTaskMonitor {

    private final CraneTaskFollowUpRecordRepository followUpRecordRepository;
    private final CraneTaskQueryService craneTaskQueryService;
    private final CraneStatusCache craneStatusCache;
    private final CraneCommandCache craneCommandCache;
    private final PlcCraneRegistry plcCraneRegistry;
    private final CraneHandshakeStateMachine handshakeStateMachine;

    /**
     * 每 1 秒輪詢所有 crane 狀態，並交由狀態機推進交握流程
     */
    @Scheduled(fixedDelay = 150)
    public void monitorAllCraneTasks() {
        for (String craneName : plcCraneRegistry.getAllCraneNames()) {
            CraneDeviceStatus deviceStatus = craneStatusCache.getLatest(craneName);
            if (deviceStatus == null || !deviceStatus.isValidAndComplete(3)) continue;

            int craneId = deviceStatus.getCraneId(); // 轉為整數
            CraneCommandStatus commandStatus = craneCommandCache.getLatest(craneId);
            CraneCommandStatus lastWrite = craneCommandCache.getLastWriteCommand(craneId);

            if (commandStatus != null && lastWrite != null) {
                commandStatus.setLastWriteCommand(lastWrite); // 注入歷史資料
            }

            List<CraneTaskFollowUpRecord> pendingRecords = followUpRecordRepository.findAll().stream()
                    .filter(r -> Boolean.FALSE.equals(r.getHandled()))
                    .toList();

            if (!pendingRecords.isEmpty()) continue;

            craneTaskQueryService.findTopPriorityTaskByCrane(craneId).ifPresent(task -> {
                try {
                    handshakeStateMachine.tick(task, deviceStatus, commandStatus); // 改為三參數
                } catch (Exception e) {
                    log.error("[Monitor] Crane#{} 任務#{} 發生錯誤：{}", craneId, task.getId(), e.getMessage(), e);
                }
            });
        }
    }
}
