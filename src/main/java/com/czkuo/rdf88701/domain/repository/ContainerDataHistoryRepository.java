package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ContainerDataHistory;
import java.util.List;
import java.util.Optional;

public interface ContainerDataHistoryRepository {

    Optional<ContainerDataHistory> findById(Long id);

    boolean save(ContainerDataHistory entity);

    boolean update(ContainerDataHistory entity);

    boolean deleteById(Long id);

    List<ContainerDataHistory> findAll();
}
