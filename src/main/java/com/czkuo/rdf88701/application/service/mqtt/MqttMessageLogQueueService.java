package com.czkuo.rdf88701.application.service.mqtt;

import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.repository.MqttMessageLogRepository;
import com.czkuo.rdf88701.infra.entity.MqttMessageLog;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttMessageLogQueueService {

    private final MqttMessageLogRepository logRepository;

    @Value("${mqtt.audit.queue.enabled:true}")
    private boolean enabled;

    @Value("${mqtt.audit.queue.max-size:5000}")
    private int maxSize;

    @Value("${mqtt.audit.queue.batch-size:100}")
    private int batchSize;

    @Value("${mqtt.audit.queue.flush-interval-ms:300}")
    private long flushIntervalMs;

    private BlockingQueue<MqttMessageLog> queue;
    private ScheduledExecutorService worker;
    private final AtomicLong droppedCount = new AtomicLong();

    @PostConstruct
    public void start() {
        int capacity = Math.max(1, maxSize);
        batchSize = Math.max(1, batchSize);
        flushIntervalMs = Math.max(50, flushIntervalMs);
        queue = new ArrayBlockingQueue<>(capacity);

        if (!enabled) {
            log.info("[MQTT][LOG-QUEUE] disabled; record() will write synchronously");
            return;
        }

        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mqtt-log-batch-writer");
            t.setDaemon(true);
            return t;
        });
        worker.scheduleWithFixedDelay(this::flushSafely, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
        log.info("[MQTT][LOG-QUEUE] started capacity={}, batchSize={}, flushIntervalMs={}",
                capacity, batchSize, flushIntervalMs);
    }

    public void enqueue(MqttMessageLog logRow, MqttMessageType type) {
        if (logRow == null) {
            return;
        }
        if (!enabled || queue == null) {
            saveSynchronously(logRow);
            return;
        }

        if (queue.offer(logRow)) {
            return;
        }

        if (isCritical(type)) {
            log.warn("[MQTT][LOG-QUEUE] full; writing critical log synchronously tid={}, cmdId={}, type={}",
                    logRow.getTid(), logRow.getCmdId(), type);
            saveSynchronously(logRow);
            return;
        }

        long dropped = droppedCount.incrementAndGet();
        if (dropped == 1 || dropped % 1000 == 0) {
            log.warn("[MQTT][LOG-QUEUE] full; dropped non-critical audit log count={}, latest tid={}, cmdId={}, type={}",
                    dropped, logRow.getTid(), logRow.getCmdId(), type);
        }
    }

    private boolean isCritical(MqttMessageType type) {
        return type == MqttMessageType.COMMAND || type == MqttMessageType.ACK;
    }

    private void flushSafely() {
        try {
            flushOnce();
        } catch (Exception e) {
            log.error("[MQTT][LOG-QUEUE] flush failed: {}", e.getMessage(), e);
        }
    }

    private void flushOnce() {
        if (queue == null || queue.isEmpty()) {
            return;
        }

        List<MqttMessageLog> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        if (batch.isEmpty()) {
            return;
        }

        try {
            boolean saved = logRepository.saveBatch(batch);
            if (!saved) {
                throw new IllegalStateException("batch insert affected rows mismatch");
            }
        } catch (Exception batchEx) {
            log.error("[MQTT][LOG-QUEUE] batch insert failed; fallback to single insert, size={}, err={}",
                    batch.size(), batchEx.getMessage(), batchEx);
            for (MqttMessageLog logRow : batch) {
                saveSynchronously(logRow);
            }
        }
    }

    private void saveSynchronously(MqttMessageLog logRow) {
        try {
            logRepository.save(logRow);
        } catch (Exception e) {
            log.error("[MQTT][LOG-QUEUE] synchronous insert failed: tid={}, cmdId={}, err={}",
                    logRow.getTid(), logRow.getCmdId(), e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stop() {
        if (worker != null) {
            worker.shutdown();
        }
        if (queue == null) {
            return;
        }

        while (!queue.isEmpty()) {
            flushSafely();
        }
        log.info("[MQTT][LOG-QUEUE] stopped; droppedCount={}", droppedCount.get());
    }
}
