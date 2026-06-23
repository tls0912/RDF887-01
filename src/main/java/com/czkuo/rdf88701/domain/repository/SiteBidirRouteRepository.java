package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.SiteBidirRoute;
import java.util.List;
import java.util.Optional;

public interface SiteBidirRouteRepository {

    Optional<SiteBidirRoute> findById(Long id);

    boolean save(SiteBidirRoute entity);

    boolean update(SiteBidirRoute entity);

    boolean deleteById(Long id);

    List<SiteBidirRoute> findAll();
}
