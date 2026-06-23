package com.czkuo.rdf88701.infra.event.model.system;

import org.springframework.context.ApplicationEvent;

public class PlcPollingReadyEvent extends ApplicationEvent {
    public PlcPollingReadyEvent(Object source) {
        super(source);
    }
}
