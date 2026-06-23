package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.WorkingBeamTaskHistory;
import java.util.List;
import java.util.Optional;

public interface WorkingBeamTaskHistoryRepository {

    Optional<WorkingBeamTaskHistory> findById(Long id);

    boolean save(WorkingBeamTaskHistory entity);

    boolean update(WorkingBeamTaskHistory entity);

    boolean deleteById(Long id);

    List<WorkingBeamTaskHistory> findAll();
}
