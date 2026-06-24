package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.MqttInboxStatusLog;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface MqttInboxStatusLogRepository {

    Optional<MqttInboxStatusLog> findById(Long id);

    boolean save(MqttInboxStatusLog entity);

    boolean update(MqttInboxStatusLog entity);

    boolean deleteById(Long id);

    List<MqttInboxStatusLog> findAll();
}
