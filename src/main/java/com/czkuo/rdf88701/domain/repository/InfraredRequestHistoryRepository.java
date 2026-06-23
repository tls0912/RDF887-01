package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.InfraredRequestHistory;
import java.util.List;
import java.util.Optional;

public interface InfraredRequestHistoryRepository {

    Optional<InfraredRequestHistory> findById(Long id);

    boolean save(InfraredRequestHistory entity);

    boolean update(InfraredRequestHistory entity);

    boolean deleteById(Long id);

    List<InfraredRequestHistory> findAll();
}
