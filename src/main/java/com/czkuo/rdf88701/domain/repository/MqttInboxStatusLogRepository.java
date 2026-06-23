package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.MqttInboxStatusLog;
import java.util.List;
import java.util.Optional;

public interface MqttInboxStatusLogRepository {

    Optional<MqttInboxStatusLog> findById(Long id);

    boolean save(MqttInboxStatusLog entity);

    boolean update(MqttInboxStatusLog entity);

    boolean deleteById(Long id);

    List<MqttInboxStatusLog> findAll();
}
