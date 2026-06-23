package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.GripperRequestHistory;
import java.util.List;
import java.util.Optional;

public interface GripperRequestHistoryRepository {

    Optional<GripperRequestHistory> findById(Long id);

    boolean save(GripperRequestHistory entity);

    boolean update(GripperRequestHistory entity);

    boolean deleteById(Long id);

    List<GripperRequestHistory> findAll();
}
