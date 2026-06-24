package com.czkuo.rdf88701.infra.event.model.plc.transfer;

import com.czkuo.rdf88701.infra.entity.TransferTask;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Transfer 任務完成事件（僅代表 PLC 回傳成功）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
public class TransferTaskCompletedEvent extends ApplicationEvent {

    /** 對應完成的 Transfer 任務 */
    private final TransferTask task;

    /** PLC 回傳的結果碼 */
    private final int retCode;

    /** 結果描述（通常對應 retCode 說明） */
    private final String description;

    public TransferTaskCompletedEvent(Object source, TransferTask task, int retCode, String description) {
        super(source);
        this.task = task;
        this.retCode = retCode;
        this.description = description;
    }
}