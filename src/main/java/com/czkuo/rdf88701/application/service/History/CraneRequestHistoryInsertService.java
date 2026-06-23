package com.czkuo.rdf88701.application.service.History;

import com.czkuo.rdf88701.domain.event.HistoryEvent;
import com.czkuo.rdf88701.infra.entity.CraneRequest;
import com.czkuo.rdf88701.infra.entity.CraneRequestHistory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CraneRequestHistoryInsertService extends GenericHistoryInsertService<CraneRequest, CraneRequestHistory> {

    @Override
    public CraneRequestHistory toHistory(HistoryEvent<CraneRequest> event) {
        CraneRequest entity = event.entity();
        CraneRequestHistory history = new CraneRequestHistory();
        BeanUtils.copyProperties(entity, history, "id");
        history.setOriginId(entity.getId());
        history.setChangeType(event.changeType());
        history.setArchivedTime(LocalDateTime.now());
        return history;
    }
}
