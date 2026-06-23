package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.RobotInR031;
import java.util.List;
import java.util.Optional;

public interface RobotInR031Repository {

    Optional<RobotInR031> findById(Long id);

    boolean save(RobotInR031 entity);

    boolean update(RobotInR031 entity);

    boolean deleteById(Long id);

    List<RobotInR031> findAll();
}
