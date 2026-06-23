package com.czkuo.rdf88701.domain.plc.state.workingbeam;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Working Beam 握手上下文資訊
 * - 每筆任務在握手過程中的狀態快照
 * - 僅支援單段式交握狀態追蹤
 */
@Data
@Builder
public class WorkingBeamHandshakeContext {

    private Long taskId;                             // 任務編號（對應 WorkingBeamTask.id）
    private String beamName;                         // WorkingBeam 名稱（如 WB#1）

    private WorkingBeamHandshakePhase phase;         // 單段交握狀態
    private Instant lastUpdatedTime;                 // 狀態最後更新時間

    private int retryCount;                          // 重試次數
    private String lastErrorMessage;                 // 最後錯誤訊息

    public boolean isTimeout(long seconds) {
        return lastUpdatedTime != null && Instant.now().minusSeconds(seconds).isAfter(lastUpdatedTime);
    }

    public void resetTimeout() {
        lastUpdatedTime = Instant.now();
    }

    public void markFailed(String message) {
        phase = WorkingBeamHandshakePhase.FAILED;
        lastErrorMessage = message;
        lastUpdatedTime = Instant.now();
    }

    public void moveTo(WorkingBeamHandshakePhase newPhase) {
        phase = newPhase;
        lastUpdatedTime = Instant.now();
    }

    public void increaseRetry() {
        retryCount++;
        lastUpdatedTime = Instant.now();
    }

    public boolean isFinished() {
        return phase == WorkingBeamHandshakePhase.DONE || phase == WorkingBeamHandshakePhase.FAILED;
    }

    public boolean isDone() {
        return phase == WorkingBeamHandshakePhase.DONE;
    }

    public static WorkingBeamHandshakeContext init(Long taskId, String beamName) {
        return WorkingBeamHandshakeContext.builder()
                .taskId(taskId)
                .beamName(beamName)
                .phase(WorkingBeamHandshakePhase.NONE)
                .lastUpdatedTime(Instant.now())
                .retryCount(0)
                .build();
    }
}
