package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ProductData;
import java.util.List;
import java.util.Optional;

public interface ProductDataRepository {

    Optional<ProductData> findById(Long id);

    boolean save(ProductData entity);

    boolean update(ProductData entity);

    boolean deleteById(Long id);

    List<ProductData> findAll();
}
