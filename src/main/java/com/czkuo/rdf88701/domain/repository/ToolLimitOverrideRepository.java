package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ToolLimitOverride;
import java.util.List;
import java.util.Optional;

public interface ToolLimitOverrideRepository {

    Optional<ToolLimitOverride> findById(Long id);

    boolean save(ToolLimitOverride entity);

    boolean update(ToolLimitOverride entity);

    boolean deleteById(Long id);

    List<ToolLimitOverride> findAll();
}
