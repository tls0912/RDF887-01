package com.czkuo.rdf88701.application.service.History;

import com.czkuo.rdf88701.domain.event.HistoryEvent;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.ContainerDataHistory;
import com.czkuo.rdf88701.infra.entity.CraneRequest;
import com.czkuo.rdf88701.infra.entity.CraneRequestHistory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ContainerDataHistoryInsertService extends GenericHistoryInsertService<ContainerData, ContainerDataHistory> {

    @Override
    public ContainerDataHistory toHistory(HistoryEvent<ContainerData> event) {
        ContainerData entity = event.entity();
        ContainerDataHistory history = new ContainerDataHistory();
        BeanUtils.copyProperties(entity, history, "id");
        history.setOriginId(entity.getId());
        history.setChangeType(event.changeType());
        history.setArchivedTime(LocalDateTime.now());
        return history;
    }
}
