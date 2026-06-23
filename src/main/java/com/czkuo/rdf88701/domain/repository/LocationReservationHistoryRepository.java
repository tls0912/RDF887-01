package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.LocationReservationHistory;
import java.util.List;
import java.util.Optional;

public interface LocationReservationHistoryRepository {

    Optional<LocationReservationHistory> findById(Long id);

    boolean save(LocationReservationHistory entity);

    boolean update(LocationReservationHistory entity);

    boolean deleteById(Long id);

    List<LocationReservationHistory> findAll();
}
