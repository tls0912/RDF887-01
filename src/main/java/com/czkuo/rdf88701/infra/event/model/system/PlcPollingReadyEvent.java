package com.czkuo.rdf88701.infra.event.model.system;

import org.springframework.context.ApplicationEvent;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public class PlcPollingReadyEvent extends ApplicationEvent {
    public PlcPollingReadyEvent(Object source) {
        super(source);
    }
}
