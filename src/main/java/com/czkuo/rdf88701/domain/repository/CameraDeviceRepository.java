package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.CameraDevice;
import java.util.List;
import java.util.Optional;

public interface CameraDeviceRepository {

    Optional<CameraDevice> findById(Long id);

    boolean save(CameraDevice entity);

    boolean update(CameraDevice entity);

    boolean deleteById(Long id);

    List<CameraDevice> findAll();
}
