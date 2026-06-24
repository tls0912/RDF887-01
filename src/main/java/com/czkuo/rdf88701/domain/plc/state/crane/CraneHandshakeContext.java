package com.czkuo.rdf88701.domain.plc.state.crane;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 握手上下文資訊
 * - 每筆任務在握手過程中的狀態快照
 * - 支援 From / To 段的獨立握手追蹤
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class CraneHandshakeContext {

    private Long taskId;                        // 任務編號（對應 CraneTask.id）
    private String craneName;                   // Crane 名稱（CRANE#1）

    private CraneHandshakePhase fromPhase;      // FROM 段交握狀態
    private CraneHandshakePhase toPhase;        // TO 段交握狀態

    private Instant fromLastUpdatedTime;        // FROM 狀態最後更新時間
    private Instant toLastUpdatedTime;          // TO 狀態最後更新時間

    private int fromRetryCount;                 // FROM 重試次數
    private int toRetryCount;                   // TO 重試次數

    private String fromLastErrorMessage;        // FROM 最後錯誤訊息
    private String toLastErrorMessage;          // TO 最後錯誤訊息

    public boolean isTimeout(boolean isFrom, long seconds) {
        Instant last = isFrom ? fromLastUpdatedTime : toLastUpdatedTime;
        return last != null && Instant.now().minusSeconds(seconds).isAfter(last);
    }

    public void resetTimeout(boolean isFrom) {
        if (isFrom) fromLastUpdatedTime = Instant.now();
        else toLastUpdatedTime = Instant.now();
    }

    public void markFailed(boolean isFrom, String message) {
        if (isFrom) {
            fromPhase = CraneHandshakePhase.FAILED;
            fromLastErrorMessage = message;
            fromLastUpdatedTime = Instant.now();
        } else {
            toPhase = CraneHandshakePhase.FAILED;
            toLastErrorMessage = message;
            toLastUpdatedTime = Instant.now();
        }
    }

    public void moveTo(boolean isFrom, CraneHandshakePhase newPhase) {
        if (isFrom) {
            fromPhase = newPhase;
            fromLastUpdatedTime = Instant.now();
        } else {
            toPhase = newPhase;
            toLastUpdatedTime = Instant.now();
        }
    }

    public void increaseRetry(boolean isFrom) {
        if (isFrom) {
            fromRetryCount++;
            fromLastUpdatedTime = Instant.now();
        } else {
            toRetryCount++;
            toLastUpdatedTime = Instant.now();
        }
    }

    public boolean isFinished(boolean isFrom) {
        CraneHandshakePhase phase = isFrom ? fromPhase : toPhase;
        return phase == CraneHandshakePhase.DONE || phase == CraneHandshakePhase.FAILED;
    }

    public boolean isAllFinished() {
        return isFinished(true) && isFinished(false);
    }

    public boolean isFromDone() {
        return fromPhase == CraneHandshakePhase.DONE;
    }

    public boolean isToDone() {
        return toPhase == CraneHandshakePhase.DONE;
    }

    public CraneHandshakePhase getPhase(boolean isFrom) {
        return isFrom ? fromPhase : toPhase;
    }

    public void markSegmentDone(boolean isFrom) {
        moveTo(isFrom, CraneHandshakePhase.DONE);
    }

    public void markDone() {
        fromPhase = CraneHandshakePhase.DONE;
        toPhase = CraneHandshakePhase.DONE;
        fromLastUpdatedTime = Instant.now();
        toLastUpdatedTime = Instant.now();
    }

    public static CraneHandshakeContext init(Long taskId, String craneName) {
        return CraneHandshakeContext.builder()
                .taskId(taskId)
                .craneName(craneName)
                .fromPhase(CraneHandshakePhase.NONE)
                .toPhase(CraneHandshakePhase.NONE)
                .fromLastUpdatedTime(Instant.now())
                .toLastUpdatedTime(Instant.now())
                .fromRetryCount(0)
                .toRetryCount(0)
                .build();
    }
}
