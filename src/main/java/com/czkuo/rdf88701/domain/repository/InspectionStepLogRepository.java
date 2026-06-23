package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.InspectionStepLog;
import java.util.List;
import java.util.Optional;

public interface InspectionStepLogRepository {

    Optional<InspectionStepLog> findById(Long id);

    boolean save(InspectionStepLog entity);

    boolean update(InspectionStepLog entity);

    boolean deleteById(Long id);

    List<InspectionStepLog> findAll();
}
