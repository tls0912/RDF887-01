package com.czkuo.rdf88701.domain.plc.state.gripper;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Gripper 握手上下文資訊
 * - 每筆任務在握手過程中的狀態快照
 * - 僅支援單段式交握狀態追蹤
 */
@Data
@Builder
public class GripperHandshakeContext {

    private Long taskId;                          // 任務編號（對應 GripperTask.id）
    private String gripperName;                   // Gripper 裝置名稱（如 Gripper#1）

    private GripperHandshakePhase phase;          // 單段交握狀態
    private Instant lastUpdatedTime;              // 狀態最後更新時間

    private int retryCount;                       // 重試次數
    private String lastErrorMessage;              // 最後錯誤訊息

    public boolean isTimeout(long seconds) {
        return lastUpdatedTime != null && Instant.now().minusSeconds(seconds).isAfter(lastUpdatedTime);
    }

    public void resetTimeout() {
        lastUpdatedTime = Instant.now();
    }

    public void markFailed(String message) {
        phase = GripperHandshakePhase.FAILED;
        lastErrorMessage = message;
        lastUpdatedTime = Instant.now();
    }

    public void moveTo(GripperHandshakePhase newPhase) {
        phase = newPhase;
        lastUpdatedTime = Instant.now();
    }

    public void increaseRetry() {
        retryCount++;
        lastUpdatedTime = Instant.now();
    }

    public boolean isFinished() {
        return phase == GripperHandshakePhase.DONE || phase == GripperHandshakePhase.FAILED;
    }

    public boolean isDone() {
        return phase == GripperHandshakePhase.DONE;
    }

    public static GripperHandshakeContext init(Long taskId, String gripperName) {
        return GripperHandshakeContext.builder()
                .taskId(taskId)
                .gripperName(gripperName)
                .phase(GripperHandshakePhase.NONE)
                .lastUpdatedTime(Instant.now())
                .retryCount(0)
                .build();
    }
}
