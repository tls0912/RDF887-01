package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ProductDataHistory;
import java.util.List;
import java.util.Optional;

public interface ProductDataHistoryRepository {

    Optional<ProductDataHistory> findById(Long id);

    boolean save(ProductDataHistory entity);

    boolean update(ProductDataHistory entity);

    boolean deleteById(Long id);

    List<ProductDataHistory> findAll();
}
