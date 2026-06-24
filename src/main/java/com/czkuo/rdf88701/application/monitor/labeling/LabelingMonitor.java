package com.czkuo.rdf88701.application.monitor.labeling;

import com.czkuo.rdf88701.application.service.label.LabelingWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LabelingMonitor
 * ------------------------------------------------------------
 * 只負責定期呼叫 LabelingWorkflowService.runTick()。
 * 真正的業務流程（握手/查/列印/補償）都在 Service 內。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LabelingMonitor {

    private final LabelingWorkflowService labelingWorkflowService;

    /** 200ms 輪詢（可用設定覆蓋） */
    @Scheduled(fixedDelayString = "${labeler.monitor.fixed-delay-ms:300}")
    public void monitor() {
        try {
            labelingWorkflowService.runTick();
        } catch (Exception e) {
            log.error("[LabelingMonitor] monitor exception", e);
        }
    }
}
