package com.czkuo.rdf88701.application.service.History;

import com.czkuo.rdf88701.domain.event.HistoryEvent;
import com.czkuo.rdf88701.infra.entity.CraneRequest;
import com.czkuo.rdf88701.infra.entity.CraneRequestHistory;
import com.czkuo.rdf88701.infra.entity.CraneTask;
import com.czkuo.rdf88701.infra.entity.CraneTaskHistory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CraneTaskHistoryInsertService extends GenericHistoryInsertService<CraneTask, CraneTaskHistory> {

    @Override
    public CraneTaskHistory toHistory(HistoryEvent<CraneTask> event) {
        CraneTask entity = event.entity();
        CraneTaskHistory history = new CraneTaskHistory();
        BeanUtils.copyProperties(entity, history, "id");
        history.setOriginId(entity.getId());
        history.setChangeType(event.changeType());
        history.setArchivedTime(LocalDateTime.now());
        return history;
    }
}
