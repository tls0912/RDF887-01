package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.OcrDevice;
import java.util.List;
import java.util.Optional;

public interface OcrDeviceRepository {

    Optional<OcrDevice> findById(Integer id);

    boolean save(OcrDevice entity);

    boolean update(OcrDevice entity);

    boolean deleteById(Integer id);

    List<OcrDevice> findAll();
}
