package com.czkuo.rdf88701.application.service.History;

import com.czkuo.rdf88701.domain.event.HistoryEvent;
import com.czkuo.rdf88701.infra.entity.TransferRequest;
import com.czkuo.rdf88701.infra.entity.TransferRequestHistory;
import com.czkuo.rdf88701.infra.entity.TransferTask;
import com.czkuo.rdf88701.infra.entity.TransferTaskHistory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TransferTaskHistoryInsertService extends GenericHistoryInsertService<TransferTask, TransferTaskHistory> {

    public TransferTaskHistory toHistory(HistoryEvent<TransferTask> event) {
        TransferTask entity = event.entity();
        TransferTaskHistory history = new TransferTaskHistory();
        BeanUtils.copyProperties(entity, history, "id");
        history.setOriginId(entity.getId());
        history.setChangeType(event.changeType());
        history.setArchivedTime(LocalDateTime.now());
        return history;
    }
}
