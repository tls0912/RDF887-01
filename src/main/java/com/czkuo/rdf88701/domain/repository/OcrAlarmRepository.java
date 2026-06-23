package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.OcrAlarm;
import java.util.List;
import java.util.Optional;

public interface OcrAlarmRepository {

    Optional<OcrAlarm> findById(Long id);

    boolean save(OcrAlarm entity);

    boolean update(OcrAlarm entity);

    boolean deleteById(Long id);

    List<OcrAlarm> findAll();
}
