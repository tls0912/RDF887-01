package com.czkuo.rdf88701.application.service.History;

import com.czkuo.rdf88701.domain.event.HistoryEvent;
import com.czkuo.rdf88701.infra.entity.InfraredRequest;
import com.czkuo.rdf88701.infra.entity.InfraredRequestHistory;
import com.czkuo.rdf88701.infra.entity.InfraredTask;
import com.czkuo.rdf88701.infra.entity.InfraredTaskHistory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Service
public class InfraredTaskHistoryInsertService extends GenericHistoryInsertService<InfraredTask, InfraredTaskHistory> {

    public InfraredTaskHistory toHistory(HistoryEvent<InfraredTask> event) {
        InfraredTask entity = event.entity();
        InfraredTaskHistory history = new InfraredTaskHistory();
        BeanUtils.copyProperties(entity, history, "id");
        history.setOriginId(entity.getId());
        history.setChangeType(event.changeType());
        history.setArchivedTime(LocalDateTime.now());
        return history;
    }
}
