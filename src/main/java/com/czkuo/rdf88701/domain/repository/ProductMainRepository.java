package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ProductMain;
import java.util.List;
import java.util.Optional;

public interface ProductMainRepository {

    Optional<ProductMain> findById(Long id);

    boolean save(ProductMain entity);

    boolean update(ProductMain entity);

    boolean deleteById(Long id);

    List<ProductMain> findAll();
}
