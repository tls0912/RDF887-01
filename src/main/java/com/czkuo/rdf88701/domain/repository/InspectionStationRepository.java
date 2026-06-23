package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.InspectionStation;
import java.util.List;
import java.util.Optional;

public interface InspectionStationRepository {

    Optional<InspectionStation> findById(Long id);

    boolean save(InspectionStation entity);

    boolean update(InspectionStation entity);

    boolean deleteById(Long id);

    List<InspectionStation> findAll();
}
