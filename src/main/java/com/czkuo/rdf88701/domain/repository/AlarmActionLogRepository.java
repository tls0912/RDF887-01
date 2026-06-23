package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.AlarmActionLog;
import com.czkuo.rdf88701.infra.entity.OcrManualLog;

import java.util.List;
import java.util.Optional;

public interface AlarmActionLogRepository {

    Optional<AlarmActionLog> findById(Long id);

    boolean save(AlarmActionLog entity);

    boolean update(AlarmActionLog entity);

    boolean deleteById(Long id);

    List<AlarmActionLog> findAll();

}
