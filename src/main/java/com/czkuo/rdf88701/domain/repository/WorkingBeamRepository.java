package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.WorkingBeam;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface WorkingBeamRepository {

    Optional<WorkingBeam> findById(Long id);

    boolean save(WorkingBeam entity);

    boolean update(WorkingBeam entity);

    boolean deleteById(Long id);

    List<WorkingBeam> findAll();

    /**
     * 查詢所有啟用中的 Working Beam
     */
    List<WorkingBeam> findEnabledBeams();
}
