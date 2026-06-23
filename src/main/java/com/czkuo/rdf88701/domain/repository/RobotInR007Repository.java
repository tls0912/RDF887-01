package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.RobotInR007;
import java.util.List;
import java.util.Optional;

public interface RobotInR007Repository {

    Optional<RobotInR007> findById(Long id);

    boolean save(RobotInR007 entity);

    boolean update(RobotInR007 entity);

    boolean deleteById(Long id);

    List<RobotInR007> findAll();
}
