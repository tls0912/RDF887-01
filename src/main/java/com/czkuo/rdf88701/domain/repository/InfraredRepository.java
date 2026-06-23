package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.Infrared;
import java.util.List;
import java.util.Optional;

public interface InfraredRepository {

    Optional<Infrared> findById(Long id);

    boolean save(Infrared entity);

    boolean update(Infrared entity);

    boolean deleteById(Long id);

    List<Infrared> findAll();
}
