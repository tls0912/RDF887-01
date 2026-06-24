package com.czkuo.rdf88701.application.service.History;

import com.czkuo.rdf88701.domain.event.HistoryEvent;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequestHistory;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTaskHistory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Service
public class WorkingBeamTaskHistoryInsertService extends GenericHistoryInsertService<WorkingBeamTask, WorkingBeamTaskHistory> {

    public WorkingBeamTaskHistory toHistory(HistoryEvent<WorkingBeamTask> event) {
        WorkingBeamTask entity = event.entity();
        WorkingBeamTaskHistory history = new WorkingBeamTaskHistory();
        BeanUtils.copyProperties(entity, history, "id");
        history.setOriginId(entity.getId());
        history.setChangeType(event.changeType());
        history.setArchivedTime(LocalDateTime.now());
        return history;
    }
}
