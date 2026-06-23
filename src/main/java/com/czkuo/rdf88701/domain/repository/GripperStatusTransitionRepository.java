package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.GripperStatusTransition;
import java.util.List;
import java.util.Optional;

public interface GripperStatusTransitionRepository {

    Optional<GripperStatusTransition> findById(Long id);

    boolean save(GripperStatusTransition entity);

    boolean update(GripperStatusTransition entity);

    boolean deleteById(Long id);

    List<GripperStatusTransition> findAll();
}
