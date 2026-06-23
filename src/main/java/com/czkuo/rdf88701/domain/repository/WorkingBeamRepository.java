package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.WorkingBeam;
import java.util.List;
import java.util.Optional;

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
