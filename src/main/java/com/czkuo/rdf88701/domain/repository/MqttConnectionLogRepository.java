package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.MqttConnectionLog;
import java.util.List;
import java.util.Optional;

public interface MqttConnectionLogRepository {

    Optional<MqttConnectionLog> findById(Long id);

    boolean save(MqttConnectionLog entity);

    boolean update(MqttConnectionLog entity);

    boolean deleteById(Long id);

    List<MqttConnectionLog> findAll();
}
