package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.MqttEventStatusLog;
import java.util.List;
import java.util.Optional;

public interface MqttEventStatusLogRepository {

    Optional<MqttEventStatusLog> findById(Long id);

    boolean save(MqttEventStatusLog entity);

    boolean update(MqttEventStatusLog entity);

    boolean deleteById(Long id);

    List<MqttEventStatusLog> findAll();
}
