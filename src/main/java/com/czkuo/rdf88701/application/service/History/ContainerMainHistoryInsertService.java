package com.czkuo.rdf88701.application.service.History;

import com.czkuo.rdf88701.domain.event.HistoryEvent;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.ContainerDataHistory;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.ContainerMainHistory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ContainerMainHistoryInsertService extends GenericHistoryInsertService<ContainerMain, ContainerMainHistory> {

    @Override
    public ContainerMainHistory toHistory(HistoryEvent<ContainerMain> event) {
        ContainerMain entity = event.entity();
        ContainerMainHistory history = new ContainerMainHistory();
        BeanUtils.copyProperties(entity, history, "id");
        history.setOriginId(entity.getId());
        history.setChangeType(event.changeType());
        history.setArchivedTime(LocalDateTime.now());
        return history;
    }
}
