package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.RobotInR008;
import java.util.List;
import java.util.Optional;

public interface RobotInR008Repository {

    Optional<RobotInR008> findById(Long id);

    boolean save(RobotInR008 entity);

    boolean update(RobotInR008 entity);

    boolean deleteById(Long id);

    List<RobotInR008> findAll();
}
