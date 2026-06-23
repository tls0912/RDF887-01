package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.location.LocationSelectionService;
import com.czkuo.rdf88701.application.service.reservation.ReservationOrchestrator;
import com.czkuo.rdf88701.common.enums.CraneTaskStatus;
import com.czkuo.rdf88701.domain.repository.CraneTaskFollowUpRecordRepository;
import com.czkuo.rdf88701.domain.repository.CraneTaskRepository;
import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.infra.entity.CraneTask;
import com.czkuo.rdf88701.infra.entity.CraneTaskFollowUpRecord;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 補償任務監控器（自動處理 crane_task_follow_up_record）
 * - 定期掃描未處理紀錄
 * - 建立補償任務（RELOCATE）
 * - 鎖定 container，避免重複派任
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CraneFollowUpTaskMonitor {

    private final CraneTaskFollowUpRecordRepository followUpRecordRepository;
    private final CraneTaskRepository craneTaskRepository;
    private final ReservationOrchestrator reservationOrchestrator;
    private final LocationPointRepository locationPointRepository;

    @Scheduled(fixedDelay = 2000) // 每 2 秒掃描一次
    @Transactional
    public void scanAndCompensate() {
        List<CraneTaskFollowUpRecord> pendingRecords = followUpRecordRepository.findAll().stream()
                .filter(r -> Boolean.FALSE.equals(r.getHandled()))
                .toList();

        if (pendingRecords.isEmpty()) return;

        log.warn("[補償監控] 偵測 {} 筆未處理補償紀錄", pendingRecords.size());

        for (CraneTaskFollowUpRecord record : pendingRecords) {
            Long originalTaskId = record.getOriginalTaskId();
            Optional<CraneTask> taskOpt = craneTaskRepository.findById(originalTaskId);
            if (taskOpt.isEmpty()) {
                log.error("[補償失敗] 無法找到原任務#{}", originalTaskId);
                continue;
            }

            CraneTask failedTask = taskOpt.get();
            Long containerId = failedTask.getContainerMainId();
            String code = StringUtils.trimToEmpty(record.getReasonCode());

            // 取 Crane 位置（當作 RELOCATE 的 source）
            Long craneLocId = locationPointRepository.findByName("Crane#" + failedTask.getCraneId())
                    .map(LocationPoint::getId).orElse(null);
            if (craneLocId == null) {
                log.warn("[補償暫停] 無法解析 Crane 位置（Crane#{}），等待中... (record#{})",
                        failedTask.getCraneId(), record.getId());
                continue;
            }

            Long targetLocId = null;

            if ("0x60".equals(code)) {
                // 回原位：target = 原來源位
                Long originId = failedTask.getSourceLocationId();
                if (originId == null) {
                    log.error("[補償失敗] 0x60 缺少原來源位，無法回原位 (task#{})", failedTask.getId());
                    continue;
                }

                // 確保來源位仍被短 TTL 預留（若已存在有效預留，Orchestrator 會直接回既有）
                reservationOrchestrator.reserveOriginForOutbound(
                        containerId, originId, 300, "FOLLOW_UP", "ROLLBACK_ON_0x60");

                targetLocId = originId;
            } else if ("0xD0".equals(code)) {
                // 找新位：先預約，排除原來源與失敗目標
                java.util.Set<Long> exclude = new java.util.HashSet<>();
                if (failedTask.getTargetLocationId() != null) exclude.add(failedTask.getTargetLocationId());
                if (failedTask.getSourceLocationId() != null) exclude.add(failedTask.getSourceLocationId());

                var reservationOpt = reservationOrchestrator.reserveForInbound(
                        containerId, exclude, 300, "FOLLOW_UP", "RELOCATE_AFTER_0xD0");
                if (reservationOpt.isEmpty()) {
                    log.warn("[補償暫停] container#{} 暫無可預約儲位（0xD0），等待中...", containerId);
                    continue;
                }
                targetLocId = reservationOpt.get().getLocationPointId();
            } else {
                log.warn("[補償跳過] 未識別的 reasonCode='{}' (record#{})", code, record.getId());
                continue;
            }

            // 建立 RELOCATE 任務：source=Crane 虛擬點 → target（回原位 或 新預約位）
            CraneTask newTask = new CraneTask();
            newTask.setRequestId(failedTask.getRequestId());
            newTask.setCraneId(failedTask.getCraneId());
            newTask.setTaskType("RELOCATE");
            newTask.setTaskStatus(com.czkuo.rdf88701.common.enums.CraneTaskStatus.PENDING.name());
            newTask.setPriorityLevel(Math.max(100, failedTask.getPriorityLevel() + 10));
            newTask.setContainerMainId(containerId);
            newTask.setSourceLocationId(craneLocId);
            newTask.setTargetLocationId(targetLocId);
            newTask.setCreatedTime(java.time.LocalDateTime.now());

            craneTaskRepository.save(newTask);

            // 標記 follow-up 已處理
            record.setFollowUpTaskId(newTask.getId());
            record.setHandled(true);
            record.setHandledTime(java.time.LocalDateTime.now());
            followUpRecordRepository.update(record);

            log.info("[補償成功] ({}) container#{} 建立 RELOCATE 任務#{}：Crane#{} -> loc#{}",
                    code, containerId, newTask.getId(), failedTask.getCraneId(), targetLocId);
        }
    }
}
