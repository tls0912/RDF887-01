package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.WorkingBeamTaskHistory;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface WorkingBeamTaskHistoryRepository {

    Optional<WorkingBeamTaskHistory> findById(Long id);

    boolean save(WorkingBeamTaskHistory entity);

    boolean update(WorkingBeamTaskHistory entity);

    boolean deleteById(Long id);

    List<WorkingBeamTaskHistory> findAll();
}
