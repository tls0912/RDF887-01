package com.czkuo.rdf88701.application.service.History;

import com.czkuo.rdf88701.domain.event.HistoryEvent;
import com.czkuo.rdf88701.infra.entity.*;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TransferRequestHistoryInsertService extends GenericHistoryInsertService<TransferRequest, TransferRequestHistory> {

    public TransferRequestHistory toHistory(HistoryEvent<TransferRequest> event) {
        TransferRequest entity = event.entity();
        TransferRequestHistory history = new TransferRequestHistory();
        BeanUtils.copyProperties(entity, history, "id");
        history.setOriginId(entity.getId());
        history.setChangeType(event.changeType());
        history.setArchivedTime(LocalDateTime.now());
        return history;
    }
}
