package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.InspectionStation;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface InspectionStationRepository {

    Optional<InspectionStation> findById(Long id);

    boolean save(InspectionStation entity);

    boolean update(InspectionStation entity);

    boolean deleteById(Long id);

    List<InspectionStation> findAll();
}
