package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.AutoWalkConfig;

import java.util.List;
import java.util.Optional;

public interface AutoWalkConfigRepository {

    Optional<AutoWalkConfig> findById(Long id);

    boolean save(AutoWalkConfig entity);

    boolean update(AutoWalkConfig entity);

    boolean deleteById(Long id);

    List<AutoWalkConfig> findAll();

    List<AutoWalkConfig> findEnabledConfigs();
}
