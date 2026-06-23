package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.GripperAnomalyLog;
import java.util.List;
import java.util.Optional;

public interface GripperAnomalyLogRepository {

    Optional<GripperAnomalyLog> findById(Long id);

    boolean save(GripperAnomalyLog entity);

    boolean update(GripperAnomalyLog entity);

    boolean deleteById(Long id);

    List<GripperAnomalyLog> findAll();
}
