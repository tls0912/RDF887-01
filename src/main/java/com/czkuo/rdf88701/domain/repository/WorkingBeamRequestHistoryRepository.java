package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.WorkingBeamRequestHistory;
import java.util.List;
import java.util.Optional;

public interface WorkingBeamRequestHistoryRepository {

    Optional<WorkingBeamRequestHistory> findById(Long id);

    boolean save(WorkingBeamRequestHistory entity);

    boolean update(WorkingBeamRequestHistory entity);

    boolean deleteById(Long id);

    List<WorkingBeamRequestHistory> findAll();
}
