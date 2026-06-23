package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ContainerMainHistory;
import java.util.List;
import java.util.Optional;

public interface ContainerMainHistoryRepository {

    Optional<ContainerMainHistory> findById(Long id);

    boolean save(ContainerMainHistory entity);

    boolean update(ContainerMainHistory entity);

    boolean deleteById(Long id);

    List<ContainerMainHistory> findAll();

    List<ContainerMainHistory> findByContainerMainId(Long containerMainId);
}
