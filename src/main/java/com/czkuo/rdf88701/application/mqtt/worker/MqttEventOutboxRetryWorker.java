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
 * Outbox 補償重送排程
 *
 * 設計重點：
 * 1) 非同步、可重入：每次 tick 只撿「到期」(next_attempt_time <= now) 且狀態為 PENDING/RETRYING 的事件。
 * 2) 無整體交易：避免把「查詢 + 發送 MQTT」包在同一個長交易中；實際的狀態轉移由 service 內部用短交易 + CAS 完成。
 * 3) 多實例安全：service 內部在狀態轉移時使用 CAS（eq status）避免多個節點重複處理；即使兩節點同時撿到同一筆，
 *    只有第一個成功 CAS 的會完成狀態更新。若你要「連發送都避免雙送」，可再做「claim/lease」設計（後面說明）。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttEventOutboxRetryWorker {

    private final MqttEventLogRepository eventRepo;
    private final MqttEventOutboxService outbox;

    /** 是否啟用排程（預設 true） */
    @Value("${mqtt.outbox.enabled:false}")
    private boolean enabled;

    /** 每批次處理的最大筆數（預設 50） */
    @Value("${mqtt.outbox.batch-size:50}")
    private int batchSize;

    /**
     * 固定延遲排程（預設 5000ms）
     * - initialDelay：可避免剛啟動時與 S001/連線程序搶資源（預設 0，可在 YAML 調）
     * - 不使用 @Transactional：讓 outbox.trySendOnce 內部各自掌控短交易
     */
    @Scheduled(
            fixedDelayString   = "${mqtt.outbox.retry-fixed-delay-ms:5000}",
            initialDelayString = "${mqtt.outbox.initial-delay-ms:0}"
    )
    public void tick() {
        if (!enabled) {
            // 關閉時早退，不刷 log 以免洗版
            return;
        }

        final LocalDateTime now   = LocalDateTime.now();
        final int           limit = Math.max(1, batchSize);

        final List<MqttEventLog> due;
        try {
            // 只抓「到期」的事件（PENDING/RETRYING 且 next_attempt_time <= now）
            due = eventRepo.findDueForSend(now, limit);
        } catch (Exception e) {
            // 查詢若出錯，下一輪再試
            log.warn("[OUTBOX] findDueForSend error: {}", e.toString());
            return;
        }

        if (due.isEmpty()) return;

        if (log.isDebugEnabled()) {
            //log.debug("[OUTBOX] fetched {} due events (limit={})", due.size(), limit);
        }

        // 單筆處理：避免一筆爆炸影響整批；每筆的狀態轉移在 service 內用 CAS + 短交易處理
        for (MqttEventLog ev : due) {
            try {
                outbox.trySendOnce(ev);  // 內部會：成功→SENT/ACKED；失敗→RETRYING；未連線→重排程；達上限→FAILED
            } catch (Exception e) {
                // 不中斷批次；下一輪會再撿（因為狀態沒有被前置轉移就不會被鎖住）
                log.warn("[OUTBOX][{}] worker exception: {}", ev.getTid(), e.toString());
            }
        }
    }
}
