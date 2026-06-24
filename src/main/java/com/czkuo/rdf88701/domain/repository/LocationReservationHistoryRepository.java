package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.LocationReservationHistory;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface LocationReservationHistoryRepository {

    Optional<LocationReservationHistory> findById(Long id);

    boolean save(LocationReservationHistory entity);

    boolean update(LocationReservationHistory entity);

    boolean deleteById(Long id);

    List<LocationReservationHistory> findAll();
}
