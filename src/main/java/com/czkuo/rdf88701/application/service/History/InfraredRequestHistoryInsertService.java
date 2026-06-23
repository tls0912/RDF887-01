package com.czkuo.rdf88701.application.service.History;

import com.czkuo.rdf88701.domain.event.HistoryEvent;
import com.czkuo.rdf88701.infra.entity.*;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class InfraredRequestHistoryInsertService extends GenericHistoryInsertService<InfraredRequest, InfraredRequestHistory> {

    public InfraredRequestHistory toHistory(HistoryEvent<InfraredRequest> event) {
        InfraredRequest entity = event.entity();
        InfraredRequestHistory history = new InfraredRequestHistory();
        BeanUtils.copyProperties(entity, history, "id");
        history.setOriginId(entity.getId());
        history.setChangeType(event.changeType());
        history.setArchivedTime(LocalDateTime.now());
        return history;
    }
}
