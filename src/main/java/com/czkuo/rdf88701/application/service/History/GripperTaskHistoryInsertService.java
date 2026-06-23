package com.czkuo.rdf88701.application.service.History;

import com.czkuo.rdf88701.domain.event.HistoryEvent;
import com.czkuo.rdf88701.infra.entity.GripperRequest;
import com.czkuo.rdf88701.infra.entity.GripperRequestHistory;
import com.czkuo.rdf88701.infra.entity.GripperTask;
import com.czkuo.rdf88701.infra.entity.GripperTaskHistory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class GripperTaskHistoryInsertService extends GenericHistoryInsertService<GripperTask, GripperTaskHistory> {

    public GripperTaskHistory toHistory(HistoryEvent<GripperTask> event) {
        GripperTask entity = event.entity();
        GripperTaskHistory history = new GripperTaskHistory();
        BeanUtils.copyProperties(entity, history, "id");
        history.setOriginId(entity.getId());
        history.setChangeType(event.changeType());
        history.setArchivedTime(LocalDateTime.now());
        return history;
    }
}
