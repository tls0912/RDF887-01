package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ToolStatus;
import java.util.List;
import java.util.Optional;

public interface ToolStatusRepository {

    Optional<ToolStatus> findById(Long id);

    boolean save(ToolStatus entity);

    boolean update(ToolStatus entity);

    boolean deleteById(Long id);

    List<ToolStatus> findAll();
}
