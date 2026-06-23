package com.czkuo.rdf88701.infra.event.model.plc.workingbeam;

import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Working Beam 任務完成事件（僅代表 PLC 回傳成功）
 */
@Getter
public class WorkingBeamTaskCompletedEvent extends ApplicationEvent {

    /** 對應完成的 Working Beam 任務 */
    private final WorkingBeamTask task;

    /** PLC 回傳的結果碼 */
    private final int retCode;

    /** 結果描述（通常對應 retCode 說明） */
    private final String description;

    public WorkingBeamTaskCompletedEvent(Object source, WorkingBeamTask task, int retCode, String description) {
        super(source);
        this.task = task;
        this.retCode = retCode;
        this.description = description;
    }
}
