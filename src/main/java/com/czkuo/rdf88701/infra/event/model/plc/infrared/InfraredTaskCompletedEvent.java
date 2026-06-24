package com.czkuo.rdf88701.infra.event.model.plc.infrared;

import com.czkuo.rdf88701.infra.entity.InfraredTask;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


/**
 * Infrared 任務完成事件（攜帶 PLC 回傳的高度/層數等測量數據）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
public class InfraredTaskCompletedEvent extends ApplicationEvent {

    /** 對應完成的 Infrared 任務 */
    private final InfraredTask task;

    /** 測量數據：主高度（如 tray stack 高度 1） */
    private final int productHeight1;

    /** 測量數據：副高度（如另一側） */
    private final int productHeight2;

    /** 測量數據：PLC 報的層數 */
    private final int productQuantity;

    /** PLC 回傳的結果碼 */
    private final int retCode;

    /** 結果描述（通常對應 retCode 說明） */
    private final String description;

    public InfraredTaskCompletedEvent(
            Object source,
            InfraredTask task,
            int productHeight1,
            int productHeight2,
            int productQuantity,
            int retCode,
            String description
    ) {
        super(source);
        this.task = task;
        this.productHeight1 = productHeight1;
        this.productHeight2 = productHeight2;
        this.productQuantity = productQuantity;
        this.retCode = retCode;
        this.description = description;
    }
}
