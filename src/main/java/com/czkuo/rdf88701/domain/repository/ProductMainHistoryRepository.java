package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ProductMainHistory;
import java.util.List;
import java.util.Optional;

public interface ProductMainHistoryRepository {

    Optional<ProductMainHistory> findById(Long id);

    boolean save(ProductMainHistory entity);

    boolean update(ProductMainHistory entity);

    boolean deleteById(Long id);

    List<ProductMainHistory> findAll();
}
