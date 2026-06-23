package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ToolCatalog;
import java.util.List;
import java.util.Optional;

public interface ToolCatalogRepository {

    Optional<ToolCatalog> findById(Long id);

    boolean save(ToolCatalog entity);

    boolean update(ToolCatalog entity);

    boolean deleteById(Long id);

    List<ToolCatalog> findAll();
}
