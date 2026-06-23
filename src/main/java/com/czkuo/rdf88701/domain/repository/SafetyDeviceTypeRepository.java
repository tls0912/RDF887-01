package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.SafetyDeviceType;
import java.util.List;
import java.util.Optional;

public interface SafetyDeviceTypeRepository {

    Optional<SafetyDeviceType> findById(Long id);

    boolean save(SafetyDeviceType entity);

    boolean update(SafetyDeviceType entity);

    boolean deleteById(Long id);

    List<SafetyDeviceType> findAll();
}
