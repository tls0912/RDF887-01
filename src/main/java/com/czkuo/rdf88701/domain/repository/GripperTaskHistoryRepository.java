package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.GripperTaskHistory;
import java.util.List;
import java.util.Optional;

public interface GripperTaskHistoryRepository {

    Optional<GripperTaskHistory> findById(Long id);

    boolean save(GripperTaskHistory entity);

    boolean update(GripperTaskHistory entity);

    boolean deleteById(Long id);

    List<GripperTaskHistory> findAll();
}
