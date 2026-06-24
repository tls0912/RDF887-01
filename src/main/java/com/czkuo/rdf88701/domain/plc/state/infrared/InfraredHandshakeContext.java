package com.czkuo.rdf88701.domain.plc.state.infrared;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Infrared 握手上下文資訊
 * - 每筆任務在握手過程中的狀態快照
 * - 僅支援單段式交握狀態追蹤
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class InfraredHandshakeContext {

    private Long taskId;                            // 任務編號（對應 InfraredTask.id）
    private String sensorName;                      // Infrared 名稱（如 Infrared#1）

    private InfraredHandshakePhase phase;           // 單段交握狀態
    private Instant lastUpdatedTime;                // 狀態最後更新時間

    private int retryCount;                         // 重試次數
    private String lastErrorMessage;                // 最後錯誤訊息

    public boolean isTimeout(long seconds) {
        return lastUpdatedTime != null && Instant.now().minusSeconds(seconds).isAfter(lastUpdatedTime);
    }

    public void resetTimeout() {
        lastUpdatedTime = Instant.now();
    }

    public void markFailed(String message) {
        phase = InfraredHandshakePhase.FAILED;
        lastErrorMessage = message;
        lastUpdatedTime = Instant.now();
    }

    public void moveTo(InfraredHandshakePhase newPhase) {
        phase = newPhase;
        lastUpdatedTime = Instant.now();
    }

    public void increaseRetry() {
        retryCount++;
        lastUpdatedTime = Instant.now();
    }

    public boolean isFinished() {
        return phase == InfraredHandshakePhase.DONE || phase == InfraredHandshakePhase.FAILED;
    }

    public boolean isDone() {
        return phase == InfraredHandshakePhase.DONE;
    }

    public static InfraredHandshakeContext init(Long taskId, String sensorName) {
        return InfraredHandshakeContext.builder()
                .taskId(taskId)
                .sensorName(sensorName)
                .phase(InfraredHandshakePhase.NONE)
                .lastUpdatedTime(Instant.now())
                .retryCount(0)
                .build();
    }
}
