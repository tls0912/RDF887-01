package com.czkuo.rdf88701.application.service.History;

import com.czkuo.rdf88701.domain.event.HistoryEvent;
import com.czkuo.rdf88701.infra.entity.*;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Service
public class WorkingBeamRequestHistoryInsertService extends GenericHistoryInsertService<WorkingBeamRequest, WorkingBeamRequestHistory> {

    public WorkingBeamRequestHistory toHistory(HistoryEvent<WorkingBeamRequest> event) {
        WorkingBeamRequest entity = event.entity();
        WorkingBeamRequestHistory history = new WorkingBeamRequestHistory();
        BeanUtils.copyProperties(entity, history, "id");
        history.setOriginId(entity.getId());
        history.setChangeType(event.changeType());
        history.setArchivedTime(LocalDateTime.now());
        return history;
    }
}
