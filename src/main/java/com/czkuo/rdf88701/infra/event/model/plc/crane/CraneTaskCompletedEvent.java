package com.czkuo.rdf88701.infra.event.model.plc.crane;

import com.czkuo.rdf88701.infra.entity.CraneTask;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Crane 任務完成事件（僅代表 PLC 回傳成功）
 */
@Getter
public class CraneTaskCompletedEvent extends ApplicationEvent {

    private final CraneTask task;
    private final String productId;
    private final double productHeight;
    private final boolean isFrom;
    private final int retCode;
    private final String description;

    public CraneTaskCompletedEvent(Object source, CraneTask task, String productId, double productHeight, boolean isFrom, int retCode, String description) {
        super(source);
        this.task = task;
        this.productId = productId;
        this.productHeight = productHeight;
        this.isFrom = isFrom;
        this.retCode = retCode;
        this.description = description;
    }
}
