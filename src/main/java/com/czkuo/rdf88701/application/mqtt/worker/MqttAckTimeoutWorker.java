package com.czkuo.rdf88701.application.mqtt.worker;

import com.czkuo.rdf88701.application.service.mqtt.MqttEventOutboxService;
import com.czkuo.rdf88701.domain.repository.MqttEventLogRepository;
import com.czkuo.rdf88701.infra.entity.MqttEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ACK 逾時清道夫
 *
 * 設計重點：
 * 1) 僅掃描「SENT 且 require_ack = true」且 next_attempt_time <= now 的事件，將其標記為逾時（FAILED: ack-timeout）。
 * 2) 不重送、不進行 RETRYING；純粹結案，讓上游依據 FAILED/ack-timeout 做後續補償或告警。
 * 3) 多實例安全：由 OutboxService 內部的 CAS（fromStatus）確保轉移原子性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttAckTimeoutWorker {

    private final MqttEventLogRepository eventRepo;
    private final MqttEventOutboxService outbox;

    /** 是否啟用逾時清理（預設 true） */
    @Value("${mqtt.outbox.ack-timeout-sweeper.enabled:true}")
    private boolean enabled;

    /** 每批次處理上限（預設 100） */
    @Value("${mqtt.outbox.ack-timeout-sweeper.batch-size:100}")
    private int batchSize;

    /**
     * 固定延遲排程（預設 2000ms）
     */
    @Scheduled(fixedDelayString = "${mqtt.outbox.ack-timeout-sweeper.fixed-delay-ms:2000}")
    public void sweep() {
        if (!enabled) return;

        LocalDateTime now = LocalDateTime.now();
        int limit = Math.max(1, batchSize);

        List<MqttEventLog> overdue;
        try {
            // 取出 SENT & require_ack=true & next_attempt_time <= :now
            overdue = eventRepo.findWaitingAckOverdue(now, limit);
        } catch (Exception e) {
            log.warn("[ACK-TIMEOUT] query error: {}", e.toString());
            return;
        }

        if (overdue.isEmpty()) return;

        if (log.isDebugEnabled()) {
            //log.debug("[ACK-TIMEOUT] fetched {} overdue events (limit={})", overdue.size(), limit);
        }

        for (MqttEventLog ev : overdue) {
            try {
                outbox.markAckTimeout(ev);
            } catch (Exception e) {
                log.warn("[ACK-TIMEOUT][{}] mark timeout error: {}", ev.getTid(), e.toString());
            }
        }
    }
}
