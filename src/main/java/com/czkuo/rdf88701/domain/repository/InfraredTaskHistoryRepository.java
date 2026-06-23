package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.InfraredTaskHistory;
import java.util.List;
import java.util.Optional;

public interface InfraredTaskHistoryRepository {

    Optional<InfraredTaskHistory> findById(Long id);

    boolean save(InfraredTaskHistory entity);

    boolean update(InfraredTaskHistory entity);

    boolean deleteById(Long id);

    List<InfraredTaskHistory> findAll();
}
