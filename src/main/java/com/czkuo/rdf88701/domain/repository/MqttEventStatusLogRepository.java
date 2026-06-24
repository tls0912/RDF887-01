package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.MqttEventStatusLog;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface MqttEventStatusLogRepository {

    Optional<MqttEventStatusLog> findById(Long id);

    boolean save(MqttEventStatusLog entity);

    boolean update(MqttEventStatusLog entity);

    boolean deleteById(Long id);

    List<MqttEventStatusLog> findAll();
}
